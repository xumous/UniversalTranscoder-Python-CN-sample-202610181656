/****************************************************************************
** Meta object code from reading C++ file 'queuepage.h'
**
** Created by: The Qt Meta Object Compiler version 68 (Qt 6.8.1)
**
** WARNING! All changes made in this file will be lost!
*****************************************************************************/

#include "../../src/gui/queuepage.h"
#include <QtCore/qmetatype.h>

#include <QtCore/qtmochelpers.h>

#include <memory>


#include <QtCore/qxptype_traits.h>
#if !defined(Q_MOC_OUTPUT_REVISION)
#error "The header file 'queuepage.h' doesn't include <QObject>."
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
    struct qt_meta_tag_ZN9QueuePageE_t
    {
    };
} // unnamed namespace


#ifdef QT_MOC_HAS_STRINGDATA
static constexpr auto qt_meta_stringdata_ZN9QueuePageE = QtMocHelpers::stringData(
    "QueuePage",
    "statusMessage",
    "",
    "text",
    "onTaskAdded",
    "tid",
    "onTaskChanged",
    "onTaskProgress",
    "percent",
    "speed",
    "eta",
    "onAllFinished",
    "onStart",
    "onPause",
    "onResume",
    "onStop",
    "onClear",
    "onRemoveSelected",
    "onOpenOutputDir"
);
#else  // !QT_MOC_HAS_STRINGDATA
#error "qtmochelpers.h not found or too old."
#endif // !QT_MOC_HAS_STRINGDATA

Q_CONSTINIT static const uint qt_meta_data_ZN9QueuePageE[] = {

    // content:
    12, // revision
    0, // classname
    0, 0, // classinfo
    12, 14, // methods
    0, 0, // properties
    0, 0, // enums/sets
    0, 0, // constructors
    0, // flags
    1, // signalCount

    // signals: name, argc, parameters, tag, flags, initial metatype offsets
    1, 1, 86, 2, 0x06, 1 /* Public */,

    // slots: name, argc, parameters, tag, flags, initial metatype offsets
    4, 1, 89, 2, 0x0a, 3 /* Public */,
    6, 1, 92, 2, 0x0a, 5 /* Public */,
    7, 4, 95, 2, 0x0a, 7 /* Public */,
    11, 0, 104, 2, 0x0a, 12 /* Public */,
    12, 0, 105, 2, 0x08, 13 /* Private */,
    13, 0, 106, 2, 0x08, 14 /* Private */,
    14, 0, 107, 2, 0x08, 15 /* Private */,
    15, 0, 108, 2, 0x08, 16 /* Private */,
    16, 0, 109, 2, 0x08, 17 /* Private */,
    17, 0, 110, 2, 0x08, 18 /* Private */,
    18, 0, 111, 2, 0x08, 19 /* Private */,

    // signals: parameters
    QMetaType::Void, QMetaType::QString, 3,

    // slots: parameters
    QMetaType::Void, QMetaType::QString, 5,
    QMetaType::Void, QMetaType::QString, 5,
    QMetaType::Void, QMetaType::QString, QMetaType::Double, QMetaType::Double, QMetaType::Double, 5, 8, 9, 10,
    QMetaType::Void,
    QMetaType::Void,
    QMetaType::Void,
    QMetaType::Void,
    QMetaType::Void,
    QMetaType::Void,
    QMetaType::Void,
    QMetaType::Void,

    0 // eod
};

Q_CONSTINIT const QMetaObject QueuePage::staticMetaObject = {
    {
        QMetaObject::SuperData::link<QWidget::staticMetaObject>(),
        qt_meta_stringdata_ZN9QueuePageE.offsetsAndSizes,
        qt_meta_data_ZN9QueuePageE,
        qt_static_metacall,
        nullptr,
        qt_incomplete_metaTypeArray<qt_meta_tag_ZN9QueuePageE_t,
                                    // Q_OBJECT / Q_GADGET
                                    QtPrivate::TypeAndForceComplete<QueuePage, std::true_type>,
                                    // method 'statusMessage'
                                    QtPrivate::TypeAndForceComplete<void, std::false_type>,
                                    QtPrivate::TypeAndForceComplete<const QString&, std::false_type>,
                                    // method 'onTaskAdded'
                                    QtPrivate::TypeAndForceComplete<void, std::false_type>,
                                    QtPrivate::TypeAndForceComplete<const QString&, std::false_type>,
                                    // method 'onTaskChanged'
                                    QtPrivate::TypeAndForceComplete<void, std::false_type>,
                                    QtPrivate::TypeAndForceComplete<const QString&, std::false_type>,
                                    // method 'onTaskProgress'
                                    QtPrivate::TypeAndForceComplete<void, std::false_type>,
                                    QtPrivate::TypeAndForceComplete<const QString&, std::false_type>,
                                    QtPrivate::TypeAndForceComplete<double, std::false_type>,
                                    QtPrivate::TypeAndForceComplete<double, std::false_type>,
                                    QtPrivate::TypeAndForceComplete<double, std::false_type>,
                                    // method 'onAllFinished'
                                    QtPrivate::TypeAndForceComplete<void, std::false_type>,
                                    // method 'onStart'
                                    QtPrivate::TypeAndForceComplete<void, std::false_type>,
                                    // method 'onPause'
                                    QtPrivate::TypeAndForceComplete<void, std::false_type>,
                                    // method 'onResume'
                                    QtPrivate::TypeAndForceComplete<void, std::false_type>,
                                    // method 'onStop'
                                    QtPrivate::TypeAndForceComplete<void, std::false_type>,
                                    // method 'onClear'
                                    QtPrivate::TypeAndForceComplete<void, std::false_type>,
                                    // method 'onRemoveSelected'
                                    QtPrivate::TypeAndForceComplete<void, std::false_type>,
                                    // method 'onOpenOutputDir'
                                    QtPrivate::TypeAndForceComplete<void, std::false_type>
        >,
        nullptr
    }
};

void QueuePage::qt_static_metacall(QObject* _o, QMetaObject::Call _c, int _id, void** _a)
{
    auto* _t = static_cast<QueuePage*>(_o);
    if (_c == QMetaObject::InvokeMetaMethod)
    {
        switch (_id)
        {
        case 0: _t->statusMessage((*reinterpret_cast<std::add_pointer_t<QString>>(_a[1])));
            break;
        case 1: _t->onTaskAdded((*reinterpret_cast<std::add_pointer_t<QString>>(_a[1])));
            break;
        case 2: _t->onTaskChanged((*reinterpret_cast<std::add_pointer_t<QString>>(_a[1])));
            break;
        case 3: _t->onTaskProgress((*reinterpret_cast<std::add_pointer_t<QString>>(_a[1])),
                                   (*reinterpret_cast<std::add_pointer_t<double>>(_a[2])),
                                   (*reinterpret_cast<std::add_pointer_t<double>>(_a[3])),
                                   (*reinterpret_cast<std::add_pointer_t<double>>(_a[4])));
            break;
        case 4: _t->onAllFinished();
            break;
        case 5: _t->onStart();
            break;
        case 6: _t->onPause();
            break;
        case 7: _t->onResume();
            break;
        case 8: _t->onStop();
            break;
        case 9: _t->onClear();
            break;
        case 10: _t->onRemoveSelected();
            break;
        case 11: _t->onOpenOutputDir();
            break;
        default: ;
        }
    }
    if (_c == QMetaObject::IndexOfMethod)
    {
        int* result = reinterpret_cast<int*>(_a[0]);
        {
            using _q_method_type = void (QueuePage::*)(const QString&);
            if (_q_method_type _q_method = &QueuePage::statusMessage; *reinterpret_cast<_q_method_type*>(_a[1]) ==
                _q_method)
            {
                *result = 0;
                return;
            }
        }
    }
}

const QMetaObject* QueuePage::metaObject() const
{
    return QObject::d_ptr->metaObject ? QObject::d_ptr->dynamicMetaObject() : &staticMetaObject;
}

void* QueuePage::qt_metacast(const char* _clname)
{
    if (!_clname) return nullptr;
    if (!strcmp(_clname, qt_meta_stringdata_ZN9QueuePageE.stringdata0))
        return static_cast<void*>(this);
    return QWidget::qt_metacast(_clname);
}

int QueuePage::qt_metacall(QMetaObject::Call _c, int _id, void** _a)
{
    _id = QWidget::qt_metacall(_c, _id, _a);
    if (_id < 0)
        return _id;
    if (_c == QMetaObject::InvokeMetaMethod)
    {
        if (_id < 12)
            qt_static_metacall(this, _c, _id, _a);
        _id -= 12;
    }
    if (_c == QMetaObject::RegisterMethodArgumentMetaType)
    {
        if (_id < 12)
            *reinterpret_cast<QMetaType*>(_a[0]) = QMetaType();
        _id -= 12;
    }
    return _id;
}

// SIGNAL 0
void QueuePage::statusMessage(const QString& _t1)
{
    void* _a[] = {nullptr, const_cast<void*>(reinterpret_cast<const void*>(std::addressof(_t1)))};
    QMetaObject::activate(this, &staticMetaObject, 0, _a);
}

QT_WARNING_POP
