/****************************************************************************
** Meta object code from reading C++ file 'runner.h'
**
** Created by: The Qt Meta Object Compiler version 68 (Qt 6.8.1)
**
** WARNING! All changes made in this file will be lost!
*****************************************************************************/

#include "../../src/core/runner.h"
#include <QtCore/qmetatype.h>

#include <QtCore/qtmochelpers.h>

#include <memory>


#include <QtCore/qxptype_traits.h>
#if !defined(Q_MOC_OUTPUT_REVISION)
#error "The header file 'runner.h' doesn't include <QObject>."
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
    struct qt_meta_tag_ZN6RunnerE_t
    {
    };
} // unnamed namespace


#ifdef QT_MOC_HAS_STRINGDATA
static constexpr auto qt_meta_stringdata_ZN6RunnerE = QtMocHelpers::stringData(
    "Runner",
    "progressChanged",
    "",
    "ProgressInfo",
    "info",
    "processStarted",
    "pid"
);
#else  // !QT_MOC_HAS_STRINGDATA
#error "qtmochelpers.h not found or too old."
#endif // !QT_MOC_HAS_STRINGDATA

Q_CONSTINIT static const uint qt_meta_data_ZN6RunnerE[] = {

    // content:
    12, // revision
    0, // classname
    0, 0, // classinfo
    2, 14, // methods
    0, 0, // properties
    0, 0, // enums/sets
    0, 0, // constructors
    0, // flags
    2, // signalCount

    // signals: name, argc, parameters, tag, flags, initial metatype offsets
    1, 1, 26, 2, 0x06, 1 /* Public */,
    5, 1, 29, 2, 0x06, 3 /* Public */,

    // signals: parameters
    QMetaType::Void, 0x80000000 | 3, 4,
    QMetaType::Void, QMetaType::LongLong, 6,

    0 // eod
};

Q_CONSTINIT const QMetaObject Runner::staticMetaObject = {
    {
        QMetaObject::SuperData::link<QObject::staticMetaObject>(),
        qt_meta_stringdata_ZN6RunnerE.offsetsAndSizes,
        qt_meta_data_ZN6RunnerE,
        qt_static_metacall,
        nullptr,
        qt_incomplete_metaTypeArray<qt_meta_tag_ZN6RunnerE_t,
                                    // Q_OBJECT / Q_GADGET
                                    QtPrivate::TypeAndForceComplete<Runner, std::true_type>,
                                    // method 'progressChanged'
                                    QtPrivate::TypeAndForceComplete<void, std::false_type>,
                                    QtPrivate::TypeAndForceComplete<const ProgressInfo&, std::false_type>,
                                    // method 'processStarted'
                                    QtPrivate::TypeAndForceComplete<void, std::false_type>,
                                    QtPrivate::TypeAndForceComplete<qint64, std::false_type>
        >,
        nullptr
    }
};

void Runner::qt_static_metacall(QObject* _o, QMetaObject::Call _c, int _id, void** _a)
{
    auto* _t = static_cast<Runner*>(_o);
    if (_c == QMetaObject::InvokeMetaMethod)
    {
        switch (_id)
        {
        case 0: _t->progressChanged((*reinterpret_cast<std::add_pointer_t<ProgressInfo>>(_a[1])));
            break;
        case 1: _t->processStarted((*reinterpret_cast<std::add_pointer_t<qint64>>(_a[1])));
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
            case 0:
                *reinterpret_cast<QMetaType*>(_a[0]) = QMetaType::fromType<ProgressInfo>();
                break;
            }
            break;
        }
    }
    if (_c == QMetaObject::IndexOfMethod)
    {
        int* result = reinterpret_cast<int*>(_a[0]);
        {
            using _q_method_type = void (Runner::*)(const ProgressInfo&);
            if (_q_method_type _q_method = &Runner::progressChanged; *reinterpret_cast<_q_method_type*>(_a[1]) ==
                _q_method)
            {
                *result = 0;
                return;
            }
        }
        {
            using _q_method_type = void (Runner::*)(qint64);
            if (_q_method_type _q_method = &Runner::processStarted; *reinterpret_cast<_q_method_type*>(_a[1]) ==
                _q_method)
            {
                *result = 1;
                return;
            }
        }
    }
}

const QMetaObject* Runner::metaObject() const
{
    return QObject::d_ptr->metaObject ? QObject::d_ptr->dynamicMetaObject() : &staticMetaObject;
}

void* Runner::qt_metacast(const char* _clname)
{
    if (!_clname) return nullptr;
    if (!strcmp(_clname, qt_meta_stringdata_ZN6RunnerE.stringdata0))
        return static_cast<void*>(this);
    return QObject::qt_metacast(_clname);
}

int Runner::qt_metacall(QMetaObject::Call _c, int _id, void** _a)
{
    _id = QObject::qt_metacall(_c, _id, _a);
    if (_id < 0)
        return _id;
    if (_c == QMetaObject::InvokeMetaMethod)
    {
        if (_id < 2)
            qt_static_metacall(this, _c, _id, _a);
        _id -= 2;
    }
    if (_c == QMetaObject::RegisterMethodArgumentMetaType)
    {
        if (_id < 2)
            qt_static_metacall(this, _c, _id, _a);
        _id -= 2;
    }
    return _id;
}

// SIGNAL 0
void Runner::progressChanged(const ProgressInfo& _t1)
{
    void* _a[] = {nullptr, const_cast<void*>(reinterpret_cast<const void*>(std::addressof(_t1)))};
    QMetaObject::activate(this, &staticMetaObject, 0, _a);
}

// SIGNAL 1
void Runner::processStarted(qint64 _t1)
{
    void* _a[] = {nullptr, const_cast<void*>(reinterpret_cast<const void*>(std::addressof(_t1)))};
    QMetaObject::activate(this, &staticMetaObject, 1, _a);
}

QT_WARNING_POP
