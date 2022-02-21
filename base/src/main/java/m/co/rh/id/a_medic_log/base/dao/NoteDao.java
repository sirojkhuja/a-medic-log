package m.co.rh.id.a_medic_log.base.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Transaction;
import androidx.room.Update;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import m.co.rh.id.a_medic_log.base.entity.Medicine;
import m.co.rh.id.a_medic_log.base.entity.MedicineReminder;
import m.co.rh.id.a_medic_log.base.entity.Note;
import m.co.rh.id.a_medic_log.base.entity.NoteTag;
import m.co.rh.id.a_medic_log.base.state.MedicineState;
import m.co.rh.id.a_medic_log.base.state.NoteState;

@Dao
public abstract class NoteDao {

    @Query("SELECT * FROM note where profile_id = :profileId " +
            "ORDER BY entry_date_time DESC LIMIT :limit")
    public abstract List<Note> loadNotesWithLimit(long profileId, int limit);

    @Query("SELECT * FROM note WHERE content LIKE '%'||:search||'%' " +
            "ORDER BY entry_date_time DESC")
    public abstract List<Note> searchNote(String search);

    @Query("SELECT * FROM note WHERE id = :noteId")
    public abstract Note findNoteById(Long noteId);

    @Query("SELECT * FROM note WHERE id in (:noteIds)")
    public abstract List<Note> findNoteByIds(Collection<Long> noteIds);

    @Query("SELECT COUNT(*) FROM note")
    public abstract int countNote();

    @Query("SELECT COUNT(*) FROM note_tag")
    public abstract int countNoteTag();

    @Transaction
    public void insertNote(NoteState noteState) {
        Note note = noteState.getNote();
        Set<NoteTag> noteTags = noteState.getNoteTagSet();
        List<MedicineState> medicineStates = noteState.getMedicineList();
        long noteId = insert(note);
        note.id = noteId;
        if (noteTags != null && !noteTags.isEmpty()) {
            for (NoteTag noteTag : noteTags) {
                noteTag.noteId = noteId;
                noteTag.id = insert(noteTag);
            }
        }
        if (medicineStates != null && !medicineStates.isEmpty()) {
            for (MedicineState medicineState : medicineStates) {
                Medicine medicine = medicineState.getMedicine();
                medicine.noteId = noteId;
                long medicineId = insert(medicine);
                medicine.id = medicineId;
                List<MedicineReminder> medicineReminders = medicineState.getMedicineReminderList();
                if (medicineReminders != null && !medicineReminders.isEmpty()) {
                    for (MedicineReminder medicineReminder : medicineReminders) {
                        medicineReminder.medicineId = medicineId;
                        medicineReminder.id = insert(medicineReminder);
                    }
                }
            }
        }
    }

    @Transaction
    public void updateNote(NoteState noteState) {
        Note note = noteState.getNote();
        update(note);
        long noteId = note.id;
        Set<Long> toBeDeletedNoteTagIds = new LinkedHashSet<>();
        Set<Long> toBeDeletedMedicineIds = new LinkedHashSet<>();
        List<NoteTag> noteTags = findNoteTagsByNoteId(noteId);
        if (noteTags != null && !noteTags.isEmpty()) {
            for (NoteTag noteTag : noteTags) {
                toBeDeletedNoteTagIds.add(noteTag.id);
            }
        }
        List<Medicine> medicines = findMedicinesByNoteId(noteId);
        if (medicines != null && !medicines.isEmpty()) {
            for (Medicine medicine : medicines) {
                long medicineId = medicine.id;
                toBeDeletedMedicineIds.add(medicineId);
            }
        }
        Set<NoteTag> noteTagList = noteState.getNoteTagSet();
        if (noteTagList != null && !noteTagList.isEmpty()) {
            for (NoteTag noteTag : noteTagList) {
                noteTag.noteId = noteId;
                if (noteTag.id != null) {
                    update(noteTag);
                } else {
                    noteTag.id = insert(noteTag);
                }
                toBeDeletedNoteTagIds.remove(noteTag.id);
            }
        }
        List<MedicineState> medicineStates = noteState.getMedicineList();
        if (medicineStates != null && !medicineStates.isEmpty()) {
            for (MedicineState medicineState : medicineStates) {
                Medicine medicine = medicineState.getMedicine();
                medicine.noteId = noteId;
                long medicineId;
                if (medicine.id != null) {
                    medicineId = medicine.id;
                    update(medicine);
                } else {
                    medicineId = insert(medicine);
                }
                medicine.id = medicineId;
                toBeDeletedMedicineIds.remove(medicineId);
                List<MedicineReminder> medicineReminders = medicineState.getMedicineReminderList();
                if (medicineReminders != null && !medicineReminders.isEmpty()) {
                    for (MedicineReminder medicineReminder : medicineReminders) {
                        medicineReminder.medicineId = medicineId;
                        if (medicineReminder.id != null) {
                            update(medicineReminder);
                        } else {
                            medicineReminder.id = insert(medicineReminder);
                        }
                    }
                }
            }
        }

        // handle deleted note tag
        if (!toBeDeletedNoteTagIds.isEmpty()) {
            for (Long noteTagId : toBeDeletedNoteTagIds) {
                deleteNoteTagById(noteTagId);
            }
        }
        // handle deleted medicine
        if (!toBeDeletedMedicineIds.isEmpty()) {
            for (Long medicineId : toBeDeletedMedicineIds) {
                deleteMedicineReminderByMedicineId(medicineId);
                deleteMedicineIntakeByMedicineId(medicineId);
                deleteMedicineById(medicineId);
            }
        }
    }

    @Transaction
    public void deleteNote(NoteState noteState) {
        Note note = noteState.getNote();
        delete(note);
        long noteId = note.id;
        deleteNoteTagByNoteId(noteId);
        List<Medicine> medicines = findMedicinesByNoteId(noteId);
        if (medicines != null && !medicines.isEmpty()) {
            deleteMedicineByNoteId(noteId);
            for (Medicine medicine : medicines) {
                long medicineId = medicine.id;
                deleteMedicineReminderByMedicineId(medicineId);
                deleteMedicineIntakeByMedicineId(medicineId);
            }
        }
    }

    @Transaction
    public void insertNoteTag(NoteTag noteTag) {
        noteTag.id = insert(noteTag);
    }

    @Insert
    protected abstract long insert(Note note);

    @Update
    protected abstract void update(Note note);

    @Delete
    protected abstract void delete(Note note);

    @Insert
    protected abstract long insert(NoteTag noteTag);

    @Update
    protected abstract void update(NoteTag noteTag);

    @Delete
    public abstract void delete(NoteTag noteTag);

    @Query("DELETE FROM note_tag WHERE note_id = :noteId")
    protected abstract void deleteNoteTagByNoteId(long noteId);

    @Query("DELETE FROM note_tag WHERE id = :id")
    protected abstract void deleteNoteTagById(long id);

    @Query("SELECT * FROM note_tag WHERE note_id = :noteId")
    public abstract List<NoteTag> findNoteTagsByNoteId(long noteId);

    @Query("SELECT * FROM note_tag WHERE tag LIKE '%'||:search||'%' " +
            "ORDER BY tag ASC")
    public abstract List<NoteTag> searchNoteTag(String search);

    @Insert
    protected abstract long insert(Medicine medicine);

    @Update
    protected abstract void update(Medicine medicine);

    @Query("SELECT * FROM medicine WHERE note_id = :noteId")
    protected abstract List<Medicine> findMedicinesByNoteId(long noteId);

    @Query("DELETE FROM medicine WHERE note_id = :noteId")
    protected abstract void deleteMedicineByNoteId(long noteId);

    @Query("DELETE FROM medicine WHERE id = :id")
    protected abstract void deleteMedicineById(long id);

    @Insert
    protected abstract long insert(MedicineReminder medicineReminder);

    @Update
    protected abstract void update(MedicineReminder medicineReminder);

    @Query("DELETE FROM medicine_reminder WHERE medicine_id = :medicineId")
    protected abstract void deleteMedicineReminderByMedicineId(long medicineId);

    @Query("DELETE FROM medicine_intake WHERE medicine_id = :medicineId")
    protected abstract void deleteMedicineIntakeByMedicineId(long medicineId);
}
