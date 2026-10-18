/****************************************************************************
** Meta object code from reading C++ file 'taskqueue.h'
**
** Created by: The Qt Meta Object Compiler version 68 (Qt 6.8.1)
**
** WARNING! All changes made in this file will be lost!
*****************************************************************************/

#include "../../src/core/taskqueue.h"
#include <QtCore/qmetatype.h>

#include <QtCore/qtmochelpers.h>

#include <memory>


#include <QtCore/qxptype_traits.h>
#if !defined(Q_MOC_OUTPUT_REVISION)
#error "The header file 'taskqueue.h' doesn't include <QObject>."
#elif Q_MOC_OUTPUT_REVISION != 68
#error "This file was generated using the moc from 6.8.1. It"
#error "cannot be used with the include files from this version of Qt."
#error "(The moc has changed too much.)"
#endif

#ifndef Q_CONSTINIT
#define Q_CONSTINIT
#endif

QT_WARNING_PUSH
QT_WARNING_DISABLE_DEPRECATED
QT_WARNING_DISABLE_GCC (
"-Wuseless-cast"
)
namespace
{
    struct qt_meta_tag_ZN12TranscodeJobE_t
    {
    };
} // unnamed namespace


#ifdef QT_MOC_HAS_STRINGDATA
static constexpr auto qt_meta_stringdata_ZN12TranscodeJobE = QtMocHelpers::stringData(
    "TranscodeJob",
    "progress",
    "",
    "tid",
    "ProgressInfo",
    "info",
    "logLine",
    "text",
    "finished",
    "ok",
    "canceled",
    "outPath",
    "outSize",
    "elapsed",
    "Diagnosis",
    "diagnosis",
    "execute"
);
#else  // !QT_MOC_HAS_STRINGDATA
#error "qtmochelpers.h not found or too old."
#endif // !QT_MOC_HAS_STRINGDATA

Q_CONSTINIT static const uint qt_meta_data_ZN12TranscodeJobE[] = {

    // content:
    12, // revision
    0, // classname
    0, 0, // classinfo
    4, 14, // methods
    0, 0, // properties
    0, 0, // enums/sets
    0, 0, // constructors
    0, // flags
    3, // signalCount

    // signals: name, argc, parameters, tag, flags, initial metatype offsets
    1, 2, 38, 2, 0x06, 1 /* Public */,
    6, 2, 43, 2, 0x06, 4 /* Public */,
    8, 7, 48, 2, 0x06, 7 /* Public */,

    // slots: name, argc, parameters, tag, flags, initial metatype offsets
    16, 0, 63, 2, 0x0a, 15 /* Public */,

    // signals: parameters
    QMetaType::Void, QMetaType::QString, 0x80000000 | 4, 3, 5,
    QMetaType::Void, QMetaType::QString, QMetaType::QString, 3, 7,
    QMetaType::Void, QMetaType::QString, QMetaType::Bool, QMetaType::Bool, QMetaType::QString, QMetaType::LongLong,
    QMetaType::Double, 0x80000000 | 14, 3, 9, 10, 11, 12, 13, 15,

    // slots: parameters
    QMetaType::Void,

    0 // eod
};

Q_CONSTINIT const QMetaObject TranscodeJob::staticMetaObject = {
    {
        QMetaObject::SuperData::link<QObject::staticMetaObject>(),
        qt_meta_stringdata_ZN12TranscodeJobE.offsetsAndSizes,
        qt_meta_data_ZN12TranscodeJobE,
        qt_static_metacall,
        nullptr,
        qt_incomplete_metaTypeArray<qt_meta_tag_ZN12TranscodeJobE_t,
                                    // Q_OBJECT / Q_GADGET
                                    QtPrivate::TypeAndForceComplete<TranscodeJob, std::true_type>,
                                    // method 'progress'
                                    QtPrivate::TypeAndForceComplete<void, std::false_type>,
                                    QtPrivate::TypeAndForceComplete<const QString&, std::false_type>,
                                    QtPrivate::TypeAndForceComplete<const ProgressInfo&, std::false_type>,
                                    // method 'logLine'
                                    QtPrivate::TypeAndForceComplete<void, std::false_type>,
                                    QtPrivate::TypeAndForceComplete<const QString&, std::false_type>,
                                    QtPrivate::TypeAndForceComplete<const QString&, std::false_type>,
                                    // method 'finished'
                                    QtPrivate::TypeAndForceComplete<void, std::false_type>,
                                    QtPrivate::TypeAndForceComplete<const QString&, std::false_type>,
                                    QtPrivate::TypeAndForceComplete<bool, std::false_type>,
                                    QtPrivate::TypeAndForceComplete<bool, std::false_type>,
                                    QtPrivate::TypeAndForceComplete<const QString&, std::false_type>,
                                    QtPrivate::TypeAndForceComplete<qint64, std::false_type>,
                                    QtPrivate::TypeAndForceComplete<double, std::false_type>,
                                    QtPrivate::TypeAndForceComplete<const Diagnosis&, std::false_type>,
                                    // method 'execute'
                                    QtPrivate::TypeAndForceComplete<void, std::false_type>
        >,
        nullptr
    }
};

void TranscodeJob::qt_static_metacall(QObject* _o, QMetaObject::Call _c, int _id, void** _a)
{
    auto* _t = static_cast<TranscodeJob*>(_o);
    if (_c == QMetaObject::InvokeMetaMethod)
    {
        switch (_id)
        {
        case 0: _t->progress((*reinterpret_cast<std::add_pointer_t<QString>>(_a[1])),
                             (*reinterpret_cast<std::add_pointer_t<ProgressInfo>>(_a[2])));
            break;
        case 1: _t->logLine((*reinterpret_cast<std::add_pointer_t<QString>>(_a[1])),
                            (*reinterpret_cast<std::add_pointer_t<QString>>(_a[2])));
            break;
        case 2: _t->finished((*reinterpret_cast<std::add_pointer_t<QString>>(_a[1])),
                             (*reinterpret_cast<std::add_pointer_t<bool>>(_a[2])),
                             (*reinterpret_cast<std::add_pointer_t<bool>>(_a[3])),
                             (*reinterpret_cast<std::add_pointer_t<QString>>(_a[4])),
                             (*reinterpret_cast<std::add_pointer_t<qint64>>(_a[5])),
                             (*reinterpret_cast<std::add_pointer_t<double>>(_a[6])),
                             (*reinterpret_cast<std::add_pointer_t<Diagnosis>>(_a[7])));
            break;
        case 3: _t->execute();
            break;
        default: ;
        }
    }
    if (_c == QMetaObject::RegisterMethodArgumentMetaType)
    {
        switch (_id)
        {
        default: *reinterpret_cast<QMetaType*>(_a[0]) = QMetaType();
            break;
        case 0:
            switch (*reinterpret_cast<int*>(_a[1]))
            {
            default: *reinterpret_cast<QMetaType*>(_a[0]) = QMetaType();
                break;
            case 1:
                *reinterpret_cast<QMetaType*>(_a[0]) = QMetaType::fromType<ProgressInfo>();
                break;
            }
            break;
        case 2:
            switch (*reinterpret_cast<int*>(_a[1]))
            {
            default: *reinterpret_cast<QMetaType*>(_a[0]) = QMetaType();
                break;
            case 6:
                *reinterpret_cast<QMetaType*>(_a[0]) = QMetaType::fromType<Diagnosis>();
                break;
            }
            break;
        }
    }
    if (_c == QMetaObject::IndexOfMethod)
    {
        int* result = reinterpret_cast<int*>(_a[0]);
        {
            using _q_method_type = void (TranscodeJob::*)(const QString&, const ProgressInfo&);
            if (_q_method_type _q_method = &TranscodeJob::progress; *reinterpret_cast<_q_method_type*>(_a[1]) ==
                _q_method)
            {
                *result = 0;
                return;
            }
        }
        {
            using _q_method_type = void (TranscodeJob::*)(const QString&, const QString&);
            if (_q_method_type _q_method = &TranscodeJob::logLine; *reinterpret_cast<_q_method_type*>(_a[1]) ==
                _q_method)
            {
                *result = 1;
                return;
            }
        }
        {
            using _q_method_type = void (TranscodeJob::*)(const QString&, bool, bool, const QString&, qint64, double,
                                                          const Diagnosis&);
            if (_q_method_type _q_method = &TranscodeJob::finished; *reinterpret_cast<_q_method_type*>(_a[1]) ==
                _q_method)
            {
                *result = 2;
                return;
            }
        }
    }
}

const QMetaObject* TranscodeJob::metaObject() const
{
    return QObject::d_ptr->metaObject ? QObject::d_ptr->dynamicMetaObject() : &staticMetaObject;
}

void* TranscodeJob::qt_metacast(const char* _clname)
{
    if (!_clname) return nullptr;
    if (!strcmp(_clname, qt_meta_stringdata_ZN12TranscodeJobE.stringdata0))
        return static_cast<void*>(this);
    return QObject::qt_metacast(_clname);
}

int TranscodeJob::qt_metacall(QMetaObject::Call _c, int _id, void** _a)
{
    _id = QObject::qt_metacall(_c, _id, _a);
    if (_id < 0)
        return _id;
    if (_c == QMetaObject::InvokeMetaMethod)
    {
        if (_id < 4)
            qt_static_metacall(this, _c, _id, _a);
        _id -= 4;
    }
    if (_c == QMetaObject::RegisterMethodArgumentMetaType)
    {
        if (_id < 4)
            qt_static_metacall(this, _c, _id, _a);
        _id -= 4;
    }
    return _id;
}

// SIGNAL 0
void TranscodeJob::progress(const QString& _t1, const ProgressInfo& _t2)
{
    void* _a[] = {
        nullptr, const_cast<void*>(reinterpret_cast<const void*>(std::addressof(_t1))),
        const_cast<void*>(reinterpret_cast<const void*>(std::addressof(_t2)))
    };
    QMetaObject::activate(this, &staticMetaObject, 0, _a);
}

// SIGNAL 1
void TranscodeJob::logLine(const QString& _t1, const QString& _t2)
{
    void* _a[] = {
        nullptr, const_cast<void*>(reinterpret_cast<const void*>(std::addressof(_t1))),
        const_cast<void*>(reinterpret_cast<const void*>(std::addressof(_t2)))
    };
    QMetaObject::activate(this, &staticMetaObject, 1, _a);
}

// SIGNAL 2
void TranscodeJob::finished(const QString& _t1, bool _t2, bool _t3, const QString& _t4, qint64 _t5, double _t6,
                            const Diagnosis& _t7)
{
    void* _a[] = {
        nullptr, const_cast<void*>(reinterpret_cast<const void*>(std::addressof(_t1))),
        const_cast<void*>(reinterpret_cast<const void*>(std::addressof(_t2))),
        const_cast<void*>(reinterpret_cast<const void*>(std::addressof(_t3))),
        const_cast<void*>(reinterpret_cast<const void*>(std::addressof(_t4))),
        const_cast<void*>(reinterpret_cast<const void*>(std::addressof(_t5))),
        const_cast<void*>(reinterpret_cast<const void*>(std::addressof(_t6))),
        const_cast<void*>(reinterpret_cast<const void*>(std::addressof(_t7)))
    };
    QMetaObject::activate(this, &staticMetaObject, 2, _a);
}

namespace
{
    struct qt_meta_tag_ZN9TaskQueueE_t
    {
    };
} // unnamed namespace


#ifdef QT_MOC_HAS_STRINGDATA
static constexpr auto qt_meta_stringdata_ZN9TaskQueueE = QtMocHelpers::stringData(
    "TaskQueue",
    "taskAdded",
    "",
    "tid",
    "taskChanged",
    "taskProgressTick",
    "percent",
    "speed",
    "eta",
    "taskLog",
    "text",
    "allFinished",
    "onJobProgress",
    "ProgressInfo",
    "info",
    "onJobLog",
    "onJobFinished",
    "ok",
    "canceled",
    "outPath",
    "outSize",
    "elapsed",
    "Diagnosis",
    "diagnosis"
);
#else  // !QT_MOC_HAS_STRINGDATA
#error "qtmochelpers.h not found or too old."
#endif // !QT_MOC_HAS_STRINGDATA

Q_CONSTINIT static const uint qt_meta_data_ZN9TaskQueueE[] = {

    // content:
    12, // revision
    0, // classname
    0, 0, // classinfo
    8, 14, // methods
    0, 0, // properties
    0, 0, // enums/sets
    0, 0, // constructors
    0, // flags
    5, // signalCount

    // signals: name, argc, parameters, tag, flags, initial metatype offsets
    1, 1, 62, 2, 0x06, 1 /* Public */,
    4, 1, 65, 2, 0x06, 3 /* Public */,
    5, 4, 68, 2, 0x06, 5 /* Public */,
    9, 2, 77, 2, 0x06, 10 /* Public */,
    11, 0, 82, 2, 0x06, 13 /* Public */,

    // slots: name, argc, parameters, tag, flags, initial metatype offsets
    12, 2, 83, 2, 0x08, 14 /* Private */,
    15, 2, 88, 2, 0x08, 17 /* Private */,
    16, 7, 93, 2, 0x08, 20 /* Private */,

    // signals: parameters
    QMetaType::Void, QMetaType::QString, 3,
    QMetaType::Void, QMetaType::QString, 3,
    QMetaType::Void, QMetaType::QString, QMetaType::Double, QMetaType::Double, QMetaType::Double, 3, 6, 7, 8,
    QMetaType::Void, QMetaType::QString, QMetaType::QString, 3, 10,
    QMetaType::Void,

    // slots: parameters
    QMetaType::Void, QMetaType::QString, 0x80000000 | 13, 3, 14,
    QMetaType::Void, QMetaType::QString, QMetaType::QString, 3, 10,
    QMetaType::Void, QMetaType::QString, QMetaType::Bool, QMetaType::Bool, QMetaType::QString, QMetaType::LongLong,
    QMetaType::Double, 0x80000000 | 22, 3, 17, 18, 19, 20, 21, 23,

    0 // eod
};

Q_CONSTINIT const QMetaObject TaskQueue::staticMetaObject = {
    {
        QMetaObject::SuperData::link<QObject::staticMetaObject>(),
        qt_meta_stringdata_ZN9TaskQueueE.offsetsAndSizes,
        qt_meta_data_ZN9TaskQueueE,
        qt_static_metacall,
        nullptr,
        qt_incomplete_metaTypeArray<qt_meta_tag_ZN9TaskQueueE_t,
                                    // Q_OBJECT / Q_GADGET
                                    QtPrivate::TypeAndForceComplete<TaskQueue, std::true_type>,
                                    // method 'taskAdded'
                                    QtPrivate::TypeAndForceComplete<void, std::false_type>,
                                    QtPrivate::TypeAndForceComplete<const QString&, std::false_type>,
                                    // method 'taskChanged'
                                    QtPrivate::TypeAndForceComplete<void, std::false_type>,
                                    QtPrivate::TypeAndForceComplete<const QString&, std::false_type>,
                                    // method 'taskProgressTick'
                                    QtPrivate::TypeAndForceComplete<void, std::false_type>,
                                    QtPrivate::TypeAndForceComplete<const QString&, std::false_type>,
                                    QtPrivate::TypeAndForceComplete<double, std::false_type>,
                                    QtPrivate::TypeAndForceComplete<double, std::false_type>,
                                    QtPrivate::TypeAndForceComplete<double, std::false_type>,
                                    // method 'taskLog'
                                    QtPrivate::TypeAndForceComplete<void, std::false_type>,
                                    QtPrivate::TypeAndForceComplete<const QString&, std::false_type>,
                                    QtPrivate::TypeAndForceComplete<const QString&, std::false_type>,
                                    // method 'allFinished'
                                    QtPrivate::TypeAndForceComplete<void, std::false_type>,
                                    // method 'onJobProgress'
                                    QtPrivate::TypeAndForceComplete<void, std::false_type>,
                                    QtPrivate::TypeAndForceComplete<const QString&, std::false_type>,
                                    QtPrivate::TypeAndForceComplete<const ProgressInfo&, std::false_type>,
                                    // method 'onJobLog'
                                    QtPrivate::TypeAndForceComplete<void, std::false_type>,
                                    QtPrivate::TypeAndForceComplete<const QString&, std::false_type>,
                                    QtPrivate::TypeAndForceComplete<const QString&, std::false_type>,
                                    // method 'onJobFinished'
                                    QtPrivate::TypeAndForceComplete<void, std::false_type>,
                                    QtPrivate::TypeAndForceComplete<const QString&, std::false_type>,
                                    QtPrivate::TypeAndForceComplete<bool, std::false_type>,
                                    QtPrivate::TypeAndForceComplete<bool, std::false_type>,
                                    QtPrivate::TypeAndForceComplete<const QString&, std::false_type>,
                                    QtPrivate::TypeAndForceComplete<qint64, std::false_type>,
                                    QtPrivate::TypeAndForceComplete<double, std::false_type>,
                                    QtPrivate::TypeAndForceComplete<const Diagnosis&, std::false_type>
        >,
        nullptr
    }
};

void TaskQueue::qt_static_metacall(QObject* _o, QMetaObject::Call _c, int _id, void** _a)
{
    auto* _t = static_cast<TaskQueue*>(_o);
    if (_c == QMetaObject::InvokeMetaMethod)
    {
        switch (_id)
        {
        case 0: _t->taskAdded((*reinterpret_cast<std::add_pointer_t<QString>>(_a[1])));
            break;
        case 1: _t->taskChanged((*reinterpret_cast<std::add_pointer_t<QString>>(_a[1])));
            break;
        case 2: _t->taskProgressTick((*reinterpret_cast<std::add_pointer_t<QString>>(_a[1])),
                                     (*reinterpret_cast<std::add_pointer_t<double>>(_a[2])),
                                     (*reinterpret_cast<std::add_pointer_t<double>>(_a[3])),
                                     (*reinterpret_cast<std::add_pointer_t<double>>(_a[4])));
            break;
        case 3: _t->taskLog((*reinterpret_cast<std::add_pointer_t<QString>>(_a[1])),
                            (*reinterpret_cast<std::add_pointer_t<QString>>(_a[2])));
            break;
        case 4: _t->allFinished();
            break;
        case 5: _t->onJobProgress((*reinterpret_cast<std::add_pointer_t<QString>>(_a[1])),
                                  (*reinterpret_cast<std::add_pointer_t<ProgressInfo>>(_a[2])));
            break;
        case 6: _t->onJobLog((*reinterpret_cast<std::add_pointer_t<QString>>(_a[1])),
                             (*reinterpret_cast<std::add_pointer_t<QString>>(_a[2])));
            break;
        case 7: _t->onJobFinished((*reinterpret_cast<std::add_pointer_t<QString>>(_a[1])),
                                  (*reinterpret_cast<std::add_pointer_t<bool>>(_a[2])),
                                  (*reinterpret_cast<std::add_pointer_t<bool>>(_a[3])),
                                  (*reinterpret_cast<std::add_pointer_t<QString>>(_a[4])),
                                  (*reinterpret_cast<std::add_pointer_t<qint64>>(_a[5])),
                                  (*reinterpret_cast<std::add_pointer_t<double>>(_a[6])),
                                  (*reinterpret_cast<std::add_pointer_t<Diagnosis>>(_a[7])));
            break;
        default: ;
        }
    }
    if (_c == QMetaObject::RegisterMethodArgumentMetaType)
    {
        switch (_id)
        {
        default: *reinterpret_cast<QMetaType*>(_a[0]) = QMetaType();
            break;
        case 5:
            switch (*reinterpret_cast<int*>(_a[1]))
            {
            default: *reinterpret_cast<QMetaType*>(_a[0]) = QMetaType();
                break;
            case 1:
                *reinterpret_cast<QMetaType*>(_a[0]) = QMetaType::fromType<ProgressInfo>();
                break;
            }
            break;
        case 7:
            switch (*reinterpret_cast<int*>(_a[1]))
            {
            default: *reinterpret_cast<QMetaType*>(_a[0]) = QMetaType();
                break;
            case 6:
                *reinterpret_cast<QMetaType*>(_a[0]) = QMetaType::fromType<Diagnosis>();
                break;
            }
            break;
        }
    }
    if (_c == QMetaObject::IndexOfMethod)
    {
        int* result = reinterpret_cast<int*>(_a[0]);
        {
            using _q_method_type = void (TaskQueue::*)(const QString&);
            if (_q_method_type _q_method = &TaskQueue::taskAdded; *reinterpret_cast<_q_method_type*>(_a[1]) ==
                _q_method)
            {
                *result = 0;
                return;
            }
        }
        {
            using _q_method_type = void (TaskQueue::*)(const QString&);
            if (_q_method_type _q_method = &TaskQueue::taskChanged; *reinterpret_cast<_q_method_type*>(_a[1]) ==
                _q_method)
            {
                *result = 1;
                return;
            }
        }
        {
            using _q_method_type = void (TaskQueue::*)(const QString&, double, double, double);
            if (_q_method_type _q_method = &TaskQueue::taskProgressTick; *reinterpret_cast<_q_method_type*>(_a[1]) ==
                _q_method)
            {
                *result = 2;
                return;
            }
        }
        {
            using _q_method_type = void (TaskQueue::*)(const QString&, const QString&);
            if (_q_method_type _q_method = &TaskQueue::taskLog; *reinterpret_cast<_q_method_type*>(_a[1]) == _q_method)
            {
                *result = 3;
                return;
            }
        }
        {
            using _q_method_type = void (TaskQueue::*)();
            if (_q_method_type _q_method = &TaskQueue::allFinished; *reinterpret_cast<_q_method_type*>(_a[1]) ==
                _q_method)
            {
                *result = 4;
                return;
            }
        }
    }
}

const QMetaObject* TaskQueue::metaObject() const
{
    return QObject::d_ptr->metaObject ? QObject::d_ptr->dynamicMetaObject() : &staticMetaObject;
}

void* TaskQueue::qt_metacast(const char* _clname)
{
    if (!_clname) return nullptr;
    if (!strcmp(_clname, qt_meta_stringdata_ZN9TaskQueueE.stringdata0))
        return static_cast<void*>(this);
    return QObject::qt_metacast(_clname);
}

int TaskQueue::qt_metacall(QMetaObject::Call _c, int _id, void** _a)
{
    _id = QObject::qt_metacall(_c, _id, _a);
    if (_id < 0)
        return _id;
    if (_c == QMetaObject::InvokeMetaMethod)
    {
        if (_id < 8)
            qt_static_metacall(this, _c, _id, _a);
        _id -= 8;
    }
    if (_c == QMetaObject::RegisterMethodArgumentMetaType)
    {
        if (_id < 8)
            qt_static_metacall(this, _c, _id, _a);
        _id -= 8;
    }
    return _id;
}

// SIGNAL 0
void TaskQueue::taskAdded(const QString& _t1)
{
    void* _a[] = {nullptr, const_cast<void*>(reinterpret_cast<const void*>(std::addressof(_t1)))};
    QMetaObject::activate(this, &staticMetaObject, 0, _a);
}

// SIGNAL 1
void TaskQueue::taskChanged(const QString& _t1)
{
    void* _a[] = {nullptr, const_cast<void*>(reinterpret_cast<const void*>(std::addressof(_t1)))};
    QMetaObject::activate(this, &staticMetaObject, 1, _a);
}

// SIGNAL 2
void TaskQueue::taskProgressTick(const QString& _t1, double _t2, double _t3, double _t4)
{
    void* _a[] = {
        nullptr, const_cast<void*>(reinterpret_cast<const void*>(std::addressof(_t1))),
        const_cast<void*>(reinterpret_cast<const void*>(std::addressof(_t2))),
        const_cast<void*>(reinterpret_cast<const void*>(std::addressof(_t3))),
        const_cast<void*>(reinterpret_cast<const void*>(std::addressof(_t4)))
    };
    QMetaObject::activate(this, &staticMetaObject, 2, _a);
}

// SIGNAL 3
void TaskQueue::taskLog(const QString& _t1, const QString& _t2)
{
    void* _a[] = {
        nullptr, const_cast<void*>(reinterpret_cast<const void*>(std::addressof(_t1))),
        const_cast<void*>(reinterpret_cast<const void*>(std::addressof(_t2)))
    };
    QMetaObject::activate(this, &staticMetaObject, 3, _a);
}

// SIGNAL 4
void TaskQueue::allFinished()
{
    QMetaObject::activate(this, &staticMetaObject, 4, nullptr);
}

QT_WARNING_POP
