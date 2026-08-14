
## Android app

The Android app follows a layered structure so UI code does not know how data is stored:

```text
Compose screen -> ViewModel -> domain repository interface -> Room repository -> DAO -> SQLite
                                      |
                                      +-> backup repository -> Google Drive REST API
```

- `core/model/` contains plain values passed between layers. Draft models preserve unfinished text
  exactly as entered; saved models contain validated values.
- `core/result/` describes validation success and pinpoints invalid editor fields.
- `domain/repository/` defines the operations the rest of the app may perform without committing to
  Room, DataStore, or Google Drive.
- `domain/service/` contains calculations and validation that do not need Android framework APIs.
- `data/local/entity/` is the persisted Room shape; `data/mapper/` converts it to domain models.
- `data/local/dao/` owns SQL queries. Repositories coordinate multiple DAO calls and transactions.
- `data/preferences/` persists browse and theme choices with DataStore.
- `data/backup/` checkpoints Room safely and moves that checkpoint through Google Drive.
- `feature/` groups each screen's immutable UI state, ViewModel, and Compose rendering code.
- `navigation/` defines type-safe destinations; `ui/WorkoutTrackerApp.kt` joins navigation and
  feature ViewModels together.

State flows down from a ViewModel as one immutable `UiState`. User events flow back through
callbacks. A screen never edits Room directly. One-off outcomes such as a successful save use an
event flow rather than permanent UI state, preventing navigation from repeating after rotation.

## Data model

A workout has a name and date. It owns ordered workout exercises, and each workout exercise owns
ordered sets. Catalog exercises provide stable exercise identities across workouts. Workout notes
are shared by workout name; exercise notes and goals belong to catalog exercises.

Deletion and rename operations are intentionally centralized in repositories/database helpers
because they may affect several related tables. Backups checkpoint the database before upload so a
file is never copied while Room/SQLite is midway through a write.

## Where to start reading

For the Android app, begin with `MainActivity.kt`, then `WorkoutTrackerApp.kt`, one feature screen,
its ViewModel, the matching repository interface, and finally its Room implementation. For the
legacy Python apps, begin with `main.py`, then the relevant app/window class, and follow calls into
`DBHelper`.
