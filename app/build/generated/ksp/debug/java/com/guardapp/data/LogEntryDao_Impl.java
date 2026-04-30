package com.guardapp.data;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityDeletionOrUpdateAdapter;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.SharedSQLiteStatement;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import java.lang.Boolean;
import java.lang.Class;
import java.lang.Exception;
import java.lang.Float;
import java.lang.Integer;
import java.lang.Long;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import javax.annotation.processing.Generated;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlinx.coroutines.flow.Flow;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class LogEntryDao_Impl implements LogEntryDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<LogEntry> __insertionAdapterOfLogEntry;

  private final EntityDeletionOrUpdateAdapter<LogEntry> __updateAdapterOfLogEntry;

  private final SharedSQLiteStatement __preparedStmtOfMarkFalsePositive;

  private final SharedSQLiteStatement __preparedStmtOfDeleteAll;

  public LogEntryDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfLogEntry = new EntityInsertionAdapter<LogEntry>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR ABORT INTO `log_entries` (`id`,`timestamp`,`eventType`,`sensitivity`,`score`,`anchorType`,`closedAfter60s`,`isFalsePositive`) VALUES (nullif(?, 0),?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final LogEntry entity) {
        statement.bindLong(1, entity.getId());
        statement.bindLong(2, entity.getTimestamp());
        statement.bindString(3, entity.getEventType());
        if (entity.getSensitivity() == null) {
          statement.bindNull(4);
        } else {
          statement.bindString(4, entity.getSensitivity());
        }
        if (entity.getScore() == null) {
          statement.bindNull(5);
        } else {
          statement.bindDouble(5, entity.getScore());
        }
        if (entity.getAnchorType() == null) {
          statement.bindNull(6);
        } else {
          statement.bindString(6, entity.getAnchorType());
        }
        final Integer _tmp = entity.getClosedAfter60s() == null ? null : (entity.getClosedAfter60s() ? 1 : 0);
        if (_tmp == null) {
          statement.bindNull(7);
        } else {
          statement.bindLong(7, _tmp);
        }
        final int _tmp_1 = entity.isFalsePositive() ? 1 : 0;
        statement.bindLong(8, _tmp_1);
      }
    };
    this.__updateAdapterOfLogEntry = new EntityDeletionOrUpdateAdapter<LogEntry>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "UPDATE OR ABORT `log_entries` SET `id` = ?,`timestamp` = ?,`eventType` = ?,`sensitivity` = ?,`score` = ?,`anchorType` = ?,`closedAfter60s` = ?,`isFalsePositive` = ? WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final LogEntry entity) {
        statement.bindLong(1, entity.getId());
        statement.bindLong(2, entity.getTimestamp());
        statement.bindString(3, entity.getEventType());
        if (entity.getSensitivity() == null) {
          statement.bindNull(4);
        } else {
          statement.bindString(4, entity.getSensitivity());
        }
        if (entity.getScore() == null) {
          statement.bindNull(5);
        } else {
          statement.bindDouble(5, entity.getScore());
        }
        if (entity.getAnchorType() == null) {
          statement.bindNull(6);
        } else {
          statement.bindString(6, entity.getAnchorType());
        }
        final Integer _tmp = entity.getClosedAfter60s() == null ? null : (entity.getClosedAfter60s() ? 1 : 0);
        if (_tmp == null) {
          statement.bindNull(7);
        } else {
          statement.bindLong(7, _tmp);
        }
        final int _tmp_1 = entity.isFalsePositive() ? 1 : 0;
        statement.bindLong(8, _tmp_1);
        statement.bindLong(9, entity.getId());
      }
    };
    this.__preparedStmtOfMarkFalsePositive = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE log_entries SET isFalsePositive = 1 WHERE id = ?";
        return _query;
      }
    };
    this.__preparedStmtOfDeleteAll = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM log_entries";
        return _query;
      }
    };
  }

  @Override
  public Object insert(final LogEntry entry, final Continuation<? super Long> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Long>() {
      @Override
      @NonNull
      public Long call() throws Exception {
        __db.beginTransaction();
        try {
          final Long _result = __insertionAdapterOfLogEntry.insertAndReturnId(entry);
          __db.setTransactionSuccessful();
          return _result;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object update(final LogEntry entry, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __updateAdapterOfLogEntry.handle(entry);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object markFalsePositive(final long id, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfMarkFalsePositive.acquire();
        int _argIndex = 1;
        _stmt.bindLong(_argIndex, id);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfMarkFalsePositive.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteAll(final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteAll.acquire();
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfDeleteAll.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<LogEntry>> getRecent() {
    final String _sql = "SELECT * FROM log_entries ORDER BY timestamp DESC LIMIT 200";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"log_entries"}, new Callable<List<LogEntry>>() {
      @Override
      @NonNull
      public List<LogEntry> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "timestamp");
          final int _cursorIndexOfEventType = CursorUtil.getColumnIndexOrThrow(_cursor, "eventType");
          final int _cursorIndexOfSensitivity = CursorUtil.getColumnIndexOrThrow(_cursor, "sensitivity");
          final int _cursorIndexOfScore = CursorUtil.getColumnIndexOrThrow(_cursor, "score");
          final int _cursorIndexOfAnchorType = CursorUtil.getColumnIndexOrThrow(_cursor, "anchorType");
          final int _cursorIndexOfClosedAfter60s = CursorUtil.getColumnIndexOrThrow(_cursor, "closedAfter60s");
          final int _cursorIndexOfIsFalsePositive = CursorUtil.getColumnIndexOrThrow(_cursor, "isFalsePositive");
          final List<LogEntry> _result = new ArrayList<LogEntry>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final LogEntry _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final long _tmpTimestamp;
            _tmpTimestamp = _cursor.getLong(_cursorIndexOfTimestamp);
            final String _tmpEventType;
            _tmpEventType = _cursor.getString(_cursorIndexOfEventType);
            final String _tmpSensitivity;
            if (_cursor.isNull(_cursorIndexOfSensitivity)) {
              _tmpSensitivity = null;
            } else {
              _tmpSensitivity = _cursor.getString(_cursorIndexOfSensitivity);
            }
            final Float _tmpScore;
            if (_cursor.isNull(_cursorIndexOfScore)) {
              _tmpScore = null;
            } else {
              _tmpScore = _cursor.getFloat(_cursorIndexOfScore);
            }
            final String _tmpAnchorType;
            if (_cursor.isNull(_cursorIndexOfAnchorType)) {
              _tmpAnchorType = null;
            } else {
              _tmpAnchorType = _cursor.getString(_cursorIndexOfAnchorType);
            }
            final Boolean _tmpClosedAfter60s;
            final Integer _tmp;
            if (_cursor.isNull(_cursorIndexOfClosedAfter60s)) {
              _tmp = null;
            } else {
              _tmp = _cursor.getInt(_cursorIndexOfClosedAfter60s);
            }
            _tmpClosedAfter60s = _tmp == null ? null : _tmp != 0;
            final boolean _tmpIsFalsePositive;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfIsFalsePositive);
            _tmpIsFalsePositive = _tmp_1 != 0;
            _item = new LogEntry(_tmpId,_tmpTimestamp,_tmpEventType,_tmpSensitivity,_tmpScore,_tmpAnchorType,_tmpClosedAfter60s,_tmpIsFalsePositive);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Object countTriggeredSince(final long startMillis,
      final Continuation<? super Integer> $completion) {
    final String _sql = "SELECT COUNT(*) FROM log_entries WHERE eventType = 'triggered' AND timestamp >= ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, startMillis);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<Integer>() {
      @Override
      @NonNull
      public Integer call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final Integer _result;
          if (_cursor.moveToFirst()) {
            final int _tmp;
            _tmp = _cursor.getInt(0);
            _result = _tmp;
          } else {
            _result = 0;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object findById(final long id, final Continuation<? super LogEntry> $completion) {
    final String _sql = "SELECT * FROM log_entries WHERE id = ? LIMIT 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, id);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<LogEntry>() {
      @Override
      @Nullable
      public LogEntry call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "timestamp");
          final int _cursorIndexOfEventType = CursorUtil.getColumnIndexOrThrow(_cursor, "eventType");
          final int _cursorIndexOfSensitivity = CursorUtil.getColumnIndexOrThrow(_cursor, "sensitivity");
          final int _cursorIndexOfScore = CursorUtil.getColumnIndexOrThrow(_cursor, "score");
          final int _cursorIndexOfAnchorType = CursorUtil.getColumnIndexOrThrow(_cursor, "anchorType");
          final int _cursorIndexOfClosedAfter60s = CursorUtil.getColumnIndexOrThrow(_cursor, "closedAfter60s");
          final int _cursorIndexOfIsFalsePositive = CursorUtil.getColumnIndexOrThrow(_cursor, "isFalsePositive");
          final LogEntry _result;
          if (_cursor.moveToFirst()) {
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final long _tmpTimestamp;
            _tmpTimestamp = _cursor.getLong(_cursorIndexOfTimestamp);
            final String _tmpEventType;
            _tmpEventType = _cursor.getString(_cursorIndexOfEventType);
            final String _tmpSensitivity;
            if (_cursor.isNull(_cursorIndexOfSensitivity)) {
              _tmpSensitivity = null;
            } else {
              _tmpSensitivity = _cursor.getString(_cursorIndexOfSensitivity);
            }
            final Float _tmpScore;
            if (_cursor.isNull(_cursorIndexOfScore)) {
              _tmpScore = null;
            } else {
              _tmpScore = _cursor.getFloat(_cursorIndexOfScore);
            }
            final String _tmpAnchorType;
            if (_cursor.isNull(_cursorIndexOfAnchorType)) {
              _tmpAnchorType = null;
            } else {
              _tmpAnchorType = _cursor.getString(_cursorIndexOfAnchorType);
            }
            final Boolean _tmpClosedAfter60s;
            final Integer _tmp;
            if (_cursor.isNull(_cursorIndexOfClosedAfter60s)) {
              _tmp = null;
            } else {
              _tmp = _cursor.getInt(_cursorIndexOfClosedAfter60s);
            }
            _tmpClosedAfter60s = _tmp == null ? null : _tmp != 0;
            final boolean _tmpIsFalsePositive;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfIsFalsePositive);
            _tmpIsFalsePositive = _tmp_1 != 0;
            _result = new LogEntry(_tmpId,_tmpTimestamp,_tmpEventType,_tmpSensitivity,_tmpScore,_tmpAnchorType,_tmpClosedAfter60s,_tmpIsFalsePositive);
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
