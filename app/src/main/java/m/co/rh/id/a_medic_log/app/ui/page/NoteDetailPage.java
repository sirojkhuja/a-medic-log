package m.co.rh.id.a_medic_log.app.ui.page;

import android.app.Activity;
import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.RecyclerView;

import java.io.Serializable;
import java.util.Date;
import java.util.concurrent.ExecutorService;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.schedulers.Schedulers;
import m.co.rh.id.a_medic_log.R;
import m.co.rh.id.a_medic_log.app.constants.Routes;
import m.co.rh.id.a_medic_log.app.provider.StatefulViewProvider;
import m.co.rh.id.a_medic_log.app.provider.command.DeleteMedicineCmd;
import m.co.rh.id.a_medic_log.app.provider.command.NewNoteCmd;
import m.co.rh.id.a_medic_log.app.provider.command.QueryNoteCmd;
import m.co.rh.id.a_medic_log.app.provider.command.UpdateNoteCmd;
import m.co.rh.id.a_medic_log.app.provider.notifier.MedicineReminderChangeNotifier;
import m.co.rh.id.a_medic_log.app.rx.RxDisposer;
import m.co.rh.id.a_medic_log.app.ui.component.AppBarSV;
import m.co.rh.id.a_medic_log.app.ui.component.medicine.MedicineItemSV;
import m.co.rh.id.a_medic_log.app.ui.component.medicine.MedicineRecyclerViewAdapter;
import m.co.rh.id.a_medic_log.base.state.MedicineState;
import m.co.rh.id.a_medic_log.base.state.NoteState;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.anavigator.NavRoute;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.annotation.NavInject;
import m.co.rh.id.anavigator.component.INavigator;
import m.co.rh.id.anavigator.component.RequireComponent;
import m.co.rh.id.anavigator.component.RequireNavRoute;
import m.co.rh.id.anavigator.component.RequireNavigator;
import m.co.rh.id.anavigator.extension.dialog.ui.NavExtDialogConfig;
import m.co.rh.id.aprovider.Provider;

public class NoteDetailPage extends StatefulView<Activity> implements RequireNavigator, RequireNavRoute, RequireComponent<Provider>, Toolbar.OnMenuItemClickListener, View.OnClickListener, MedicineItemSV.OnMedicineIntakeListClick, MedicineItemSV.OnEditClick, MedicineItemSV.OnDeleteClick, MedicineItemSV.OnAddMedicineIntakeClick {

    private static final String TAG = NoteDetailPage.class.getName();
    private transient INavigator mNavigator;
    private transient NavRoute mNavRoute;
    @NavInject
    private AppBarSV mAppBarSv;

    private NoteState mNoteState;
    private transient ExecutorService mExecutorService;
    private transient Provider mSvProvider;
    private transient RxDisposer mRxDisposer;
    private transient MedicineReminderChangeNotifier mMedicineReminderChangeNotifier;
    private transient QueryNoteCmd mQueryNoteCmd;
    private transient NewNoteCmd mNewNoteCmd;
    private transient TextWatcher mEntryDateTimeTextWatcher;
    private transient TextWatcher mContentTextWatcher;
    private transient MedicineRecyclerViewAdapter mMedicineRecyclerViewAdapter;

    @Override
    public void provideNavigator(INavigator navigator) {
        mNavigator = navigator;
    }

    @Override
    public void provideNavRoute(NavRoute navRoute) {
        mNavRoute = navRoute;
    }

    @Override
    public void provideComponent(Provider provider) {
        mExecutorService = provider.get(ExecutorService.class);
        mSvProvider = provider.get(StatefulViewProvider.class);
        mRxDisposer = mSvProvider.get(RxDisposer.class);
        mMedicineReminderChangeNotifier = mSvProvider.get(MedicineReminderChangeNotifier.class);
        mQueryNoteCmd = mSvProvider.get(QueryNoteCmd.class);
        boolean isUpdate = isUpdate();
        if (isUpdate) {
            mNewNoteCmd = mSvProvider.get(UpdateNoteCmd.class);
        } else {
            mNewNoteCmd = mSvProvider.get(NewNoteCmd.class);
        }
        if (mNoteState == null) {
            mNoteState = new NoteState();
            if (isUpdate) {
                mNoteState.setNoteId(getNoteId());
                mRxDisposer.add("provideComponent_queryNoteInfo",
                        mSvProvider.get(QueryNoteCmd.class)
                                .queryNoteInfo(mNoteState)
                                .subscribe((noteState, throwable) -> {
                                    if (throwable != null) {
                                        mSvProvider.get(ILogger.class)
                                                .e(TAG, throwable.getMessage(), throwable);
                                    }
                                })
                );
            } else {
                mNoteState.setNoteProfileId(getProfileId());
            }
        }
        if (mAppBarSv == null) {
            mAppBarSv = new AppBarSV(R.menu.page_note_detail);
        }
        if (isUpdate) {
            mAppBarSv.setTitle(mNavigator.getActivity()
                    .getString(R.string.title_update_note));
        } else {
            mAppBarSv.setTitle(mNavigator.getActivity()
                    .getString(R.string.title_add_note));
        }
        mAppBarSv.setMenuItemListener(this);
        initTextWatcher();
        mMedicineRecyclerViewAdapter = new MedicineRecyclerViewAdapter(mNoteState,
                this, this, this, this, mNavigator, this);
    }

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        ViewGroup rootLayout = (ViewGroup) activity.getLayoutInflater().inflate(R.layout.page_note_detail, container, false);
        ViewGroup appBarContainer = rootLayout.findViewById(R.id.container_app_bar);
        appBarContainer.addView(mAppBarSv.buildView(activity, appBarContainer));
        EditText entryDateTimeInput = rootLayout.findViewById(R.id.input_text_entry_date_time);
        entryDateTimeInput.setOnClickListener(this);
        entryDateTimeInput.addTextChangedListener(mEntryDateTimeTextWatcher);
        Button clearEntryDateTimeInput = rootLayout.findViewById(R.id.button_clear_entry_date_time);
        clearEntryDateTimeInput.setOnClickListener(this);
        EditText contentInput = rootLayout.findViewById(R.id.input_text_content);
        contentInput.addTextChangedListener(mContentTextWatcher);
        Button medicineButton = rootLayout.findViewById(R.id.button_add_medicine);
        medicineButton.setOnClickListener(this);
        TextView medicineTitle = rootLayout.findViewById(R.id.text_medicine_title);
        RecyclerView medicineRecyclerView = rootLayout.findViewById(R.id.recyclerView_medicine);
        medicineRecyclerView.addItemDecoration(new DividerItemDecoration(activity, DividerItemDecoration.VERTICAL));
        medicineRecyclerView.setAdapter(mMedicineRecyclerViewAdapter);
        mRxDisposer.add("createView_onNoteChanged",
                mNoteState.getNoteFlow()
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(note -> {
                            entryDateTimeInput.setText(mNoteState.getNoteEntryDateTimeDisplay());
                            contentInput.setText(mNoteState.getNoteContent());
                        }));
        mRxDisposer.add("createView_onMedicineChanged",
                mNoteState.getMedicineListFlow()
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(medicineStates ->
                        {
                            medicineTitle.setText(activity.getString(R.string.title_medicine, medicineStates.size()));
                            mMedicineRecyclerViewAdapter.notifyItemRefreshed();
                        })
        );
        mRxDisposer
                .add("createView_onEntryDateTimeValidation",
                        mNewNoteCmd.getEntryDateTimeValid()
                                .observeOn(AndroidSchedulers.mainThread()).subscribe(error -> {
                            if (error != null && !error.isEmpty()) {
                                entryDateTimeInput.setError(error);
                            } else {
                                entryDateTimeInput.setError(null);
                            }
                        }));
        mRxDisposer
                .add("createView_onContentValidation",
                        mNewNoteCmd.getContentValid()
                                .observeOn(AndroidSchedulers.mainThread()).subscribe(error -> {
                            if (error != null && !error.isEmpty()) {
                                contentInput.setError(error);
                            } else {
                                contentInput.setError(null);
                            }
                        }));
        mRxDisposer.add("createView_onMedicineReminderAdded",
                mMedicineReminderChangeNotifier.getAddedMedicineReminder()
                        .observeOn(Schedulers.from(mExecutorService))
                        .subscribe(medicineReminder ->
                        {
                            if (isUpdate()) {
                                mQueryNoteCmd.queryMedicineInfo(mNoteState);
                            }
                        }));
        mRxDisposer.add("createView_onMedicineReminderUpdated",
                mMedicineReminderChangeNotifier.getUpdatedMedicineReminder()
                        .observeOn(Schedulers.from(mExecutorService))
                        .subscribe(medicineReminder ->
                        {
                            if (isUpdate()) {
                                mQueryNoteCmd.queryMedicineInfo(mNoteState);
                            }
                        }));
        mRxDisposer.add("createView_onMedicineReminderDeleted",
                mMedicineReminderChangeNotifier.getDeletedMedicineReminder()
                        .observeOn(Schedulers.from(mExecutorService))
                        .subscribe(medicineReminder ->
                        {
                            if (isUpdate()) {
                                mQueryNoteCmd.queryMedicineInfo(mNoteState);
                            }
                        }));
        return rootLayout;
    }

    @Override
    public void dispose(Activity activity) {
        super.dispose(activity);
        if (mSvProvider != null) {
            mSvProvider.dispose();
            mSvProvider = null;
        }
        if (mAppBarSv != null) {
            mAppBarSv.dispose(activity);
            mAppBarSv = null;
        }
        mContentTextWatcher = null;
        if (mMedicineRecyclerViewAdapter != null) {
            mMedicineRecyclerViewAdapter.dispose(activity);
            mMedicineRecyclerViewAdapter = null;
        }
    }

    @Override
    public void onEditClick(MedicineState medicineState) {
        MedicineDetailPage.Args args;
        MedicineState medicineStateArgs = medicineState.clone();
        if (isUpdate()) {
            args = MedicineDetailPage.Args.forUpdate(medicineStateArgs);
        } else {
            args = MedicineDetailPage.Args.forEdit(medicineStateArgs);
        }
        mNavigator.push(Routes.MEDICINE_DETAIL_PAGE,
                args,
                (navigator, navRoute, activity, currentView) -> {
                    MedicineDetailPage.Result result = MedicineDetailPage.Result.of(navRoute);
                    if (result != null) {
                        updateMedicineState(result.getMedicineState());
                    }
                });
    }

    @Override
    public void onDeleteClick(MedicineState medicineState) {
        if (isUpdate()) {
            Context context = mSvProvider.getContext();
            String title = context.getString(R.string.title_confirm);
            String content = context.getString(R.string.confirm_delete_medicine, medicineState.getMedicineName());
            NavExtDialogConfig navExtDialogConfig = mSvProvider.get(NavExtDialogConfig.class);
            mNavigator.push(navExtDialogConfig.getRoutePath(NavExtDialogConfig.ROUTE_CONFIRM),
                    navExtDialogConfig.args_confirmDialog(title, content),
                    (navigator, navRoute, activity, currentView) -> {
                        Provider provider = (Provider) navigator.getNavConfiguration().getRequiredComponent();
                        Boolean result = provider.get(NavExtDialogConfig.class).result_confirmDialog(navRoute);
                        if (result != null && result) {
                            confirmDeleteMedicine(medicineState);
                        }
                    });
        } else {
            mMedicineRecyclerViewAdapter.notifyItemDeleted(medicineState);
        }
    }

    private void confirmDeleteMedicine(MedicineState medicineState) {
        Context context = mSvProvider.getContext();
        mRxDisposer.add("confirmDeleteMedicine_deleteMedicineCmd",
                mSvProvider.get(DeleteMedicineCmd.class)
                        .execute(medicineState)
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe((note, throwable) -> {
                            String errorMessage = context.getString(R.string.error_failed_to_delete_medicine);
                            String successMessage = context.getString(R.string.success_deleting_medicine);
                            if (throwable != null) {
                                mSvProvider.get(ILogger.class)
                                        .e(TAG, errorMessage, throwable);
                            } else {
                                mSvProvider.get(ILogger.class)
                                        .i(TAG, successMessage);
                                mMedicineRecyclerViewAdapter.notifyItemDeleted(medicineState);
                            }
                        })
        );
    }

    @Override
    public void onAddMedicineIntakeClick(MedicineState medicineState) {
        Long medicineId = medicineState.getMedicineId();
        if (medicineId != null) {
            mNavigator.push(Routes.MEDICINE_INTAKE_DETAIL_PAGE,
                    MedicineIntakeDetailPage.Args.with(medicineId));
        }
    }

    @Override
    public void onMedicineIntakeListClick(MedicineState medicineState) {
        Long medicineId = medicineState.getMedicineId();
        if (medicineId != null) {
            mNavigator.push(Routes.MEDICINE_INTAKES_PAGE,
                    MedicineIntakeListPage.Args.with(medicineId));
        }
    }

    private void initTextWatcher() {
        if (mContentTextWatcher == null) {
            mContentTextWatcher = new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                    // Leave blank
                }

                @Override
                public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                    // Leave blank
                }

                @Override
                public void afterTextChanged(Editable editable) {
                    mNoteState.setNoteContent(editable.toString());
                    mNewNoteCmd.valid(mNoteState);
                }
            };
        }
        if (mEntryDateTimeTextWatcher == null) {
            mEntryDateTimeTextWatcher = new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                    // Leave blank
                }

                @Override
                public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                    // Leave blank
                }

                @Override
                public void afterTextChanged(Editable editable) {
                    mNewNoteCmd.valid(mNoteState);
                }
            };
        }
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.menu_save) {
            if (mNewNoteCmd.valid(mNoteState)) {
                Context context = mSvProvider.getContext();
                boolean isUpdate = isUpdate();
                mSvProvider.get(RxDisposer.class)
                        .add("onMenuItemClick_newNoteCmd.execute",
                                mNewNoteCmd.execute(mNoteState)
                                        .subscribe((noteState, throwable) -> {
                                            String errorMessage;
                                            String successMessage;
                                            if (isUpdate) {
                                                errorMessage = context.getString(R.string.error_failed_to_update_note);
                                                successMessage = context.getString(R.string.success_updating_note);
                                            } else {
                                                errorMessage = context.getString(R.string.error_failed_to_add_note);
                                                successMessage = context.getString(R.string.success_adding_note);
                                            }
                                            if (throwable != null) {
                                                mSvProvider.get(ILogger.class)
                                                        .e(TAG, errorMessage, throwable);
                                                mNavigator.pop();
                                            } else {
                                                mSvProvider.get(ILogger.class)
                                                        .i(TAG, successMessage);
                                                mNavigator.pop(Result.withNote(noteState));
                                            }
                                        }));
            } else {
                String error = mNewNoteCmd.getValidationError();
                mSvProvider.get(ILogger.class).i(TAG, error);
            }
        }
        return false;
    }

    private Long getProfileId() {
        Args args = Args.of(mNavRoute);
        if (args != null) {
            return args.getProfileId();
        }
        return null;
    }

    private Long getNoteId() {
        Args args = Args.of(mNavRoute);
        if (args != null) {
            return args.getNoteId();
        }
        return null;
    }

    @Override
    public void onClick(View view) {
        int id = view.getId();
        if (id == R.id.input_text_entry_date_time) {
            NavExtDialogConfig navExtDialogConfig = mSvProvider.get(NavExtDialogConfig.class);
            mNavigator.push(navExtDialogConfig.getRoutePath(NavExtDialogConfig.ROUTE_DATE_TIME_PICKER),
                    navExtDialogConfig.args_dateTimePickerDialog(true, mNoteState.getNoteEntryDateTime()),
                    (navigator, navRoute, activity, currentView) -> {
                        Provider provider = (Provider) navigator.getNavConfiguration().getRequiredComponent();
                        NavExtDialogConfig navExtDialogConfig1 = provider.get(NavExtDialogConfig.class);
                        Date result = navExtDialogConfig1.result_dateTimePickerDialog(navRoute);
                        if (result != null) {
                            updateEntryDateTime(result);
                        }
                    });
        } else if (id == R.id.button_clear_entry_date_time) {
            updateEntryDateTime(null);
        } else if (id == R.id.button_add_medicine) {
            MedicineDetailPage.Args args;
            if (isUpdate()) {
                args = MedicineDetailPage.Args.save(getNoteId());
            } else {
                args = MedicineDetailPage.Args.dontSave();
            }
            mNavigator.push(Routes.MEDICINE_DETAIL_PAGE,
                    args,
                    (navigator, navRoute, activity, currentView) -> {
                        MedicineDetailPage.Result result = MedicineDetailPage.Result.of(navRoute);
                        if (result != null) {
                            addMedicineState(result.getMedicineState());
                        }
                    });
        }
    }

    private void updateEntryDateTime(Date date) {
        mNoteState.updateNoteEntryDateTime(date);
    }

    private void addMedicineState(MedicineState medicineState) {
        mMedicineRecyclerViewAdapter.notifyItemAdded(medicineState);
    }

    private void updateMedicineState(MedicineState medicineState) {
        mMedicineRecyclerViewAdapter.notifyItemUpdated(medicineState);
    }

    private boolean isUpdate() {
        Args args = Args.of(mNavRoute);
        if (args != null) {
            return args.isUpdate();
        }
        return false;
    }

    public static class Result implements Serializable {
        private static Result withNote(NoteState noteState) {
            Result result = new Result();
            result.mNoteState = noteState;
            return result;
        }

        public static Result of(NavRoute navRoute) {
            if (navRoute != null) {
                return of(navRoute.getRouteResult());
            }
            return null;
        }

        public static Result of(Serializable serializable) {
            if (serializable instanceof Result) {
                return (Result) serializable;
            }
            return null;
        }

        private NoteState mNoteState;

        public NoteState getNoteState() {
            return mNoteState;
        }
    }

    public static class Args implements Serializable {
        public static Args withProfileId(long profileId) {
            Args args = new Args();
            args.mProfileId = profileId;
            return args;
        }

        public static Args forUpdate(long noteId) {
            Args args = new Args();
            args.mNoteId = noteId;
            return args;
        }

        public static Args of(NavRoute navRoute) {
            if (navRoute != null) {
                return of(navRoute.getRouteArgs());
            }
            return null;
        }

        public static Args of(Serializable serializable) {
            if (serializable instanceof Args) {
                return (Args) serializable;
            }
            return null;
        }

        private Long mProfileId;
        private Long mNoteId;

        private Args() {
        }

        public Long getProfileId() {
            return mProfileId;
        }

        public Long getNoteId() {
            return mNoteId;
        }

        public boolean isUpdate() {
            return mNoteId != null;
        }
    }
}
