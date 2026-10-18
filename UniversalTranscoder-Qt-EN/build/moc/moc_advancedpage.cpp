/****************************************************************************
** Meta object code from reading C++ file 'advancedpage.h'
**
** Created by: The Qt Meta Object Compiler version 68 (Qt 6.8.1)
**
** WARNING! All changes made in this file will be lost!
*****************************************************************************/

#include "../../src/gui/advancedpage.h"
#include <QtCore/qmetatype.h>

#include <QtCore/qtmochelpers.h>

#include <memory>


#include <QtCore/qxptype_traits.h>
#if !defined(Q_MOC_OUTPUT_REVISION)
#error "The header file 'advancedpage.h' doesn't include <QObject>."
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
    struct qt_meta_tag_ZN12AdvancedPageE_t
    {
    };
} // unnamed namespace


#ifdef QT_MOC_HAS_STRINGDATA
static constexpr auto qt_meta_stringdata_ZN12AdvancedPageE = QtMocHelpers::stringData(
    "AdvancedPage",
    "statusMessage",
    "",
    "text",
    "onBrowseSource",
    "onBrowseOutput",
    "onBuildPreview",
    "onRun",
    "onCancel",
    "onProcessFinished",
    "exitCode",
    "QProcess::ExitStatus",
    "status"
);
#else  // !QT_MOC_HAS_STRINGDATA
#error "qtmochelpers.h not found or too old."
#endif // !QT_MOC_HAS_STRINGDATA

Q_CONSTINIT static const uint qt_meta_data_ZN12AdvancedPageE[] = {

    // content:
    12, // revision
    0, // classname
    0, 0, // classinfo
    7, 14, // methods
    0, 0, // properties
    0, 0, // enums/sets
    0, 0, // constructors
    0, // flags
    1, // signalCount

    // signals: name, argc, parameters, tag, flags, initial metatype offsets
    1, 1, 56, 2, 0x06, 1 /* Public */,

    // slots: name, argc, parameters, tag, flags, initial metatype offsets
    4, 0, 59, 2, 0x08, 3 /* Private */,
    5, 0, 60, 2, 0x08, 4 /* Private */,
    6, 0, 61, 2, 0x08, 5 /* Private */,
    7, 0, 62, 2, 0x08, 6 /* Private */,
    8, 0, 63, 2, 0x08, 7 /* Private */,
    9, 2, 64, 2, 0x08, 8 /* Private */,

    // signals: parameters
    QMetaType::Void, QMetaType::QString, 3,

    // slots: parameters
    QMetaType::Void,
    QMetaType::Void,
    QMetaType::Void,
    QMetaType::Void,
    QMetaType::Void,
    QMetaType::Void, QMetaType::Int, 0x80000000 | 11, 10, 12,

    0 // eod
};

Q_CONSTINIT const QMetaObject AdvancedPage::staticMetaObject = {
    {
        QMetaObject::SuperData::link<QWidget::staticMetaObject>(),
        qt_meta_stringdata_ZN12AdvancedPageE.offsetsAndSizes,
        qt_meta_data_ZN12AdvancedPageE,
        qt_static_metacall,
        nullptr,
        qt_incomplete_metaTypeArray<qt_meta_tag_ZN12AdvancedPageE_t,
                                    // Q_OBJECT / Q_GADGET
                                    QtPrivate::TypeAndForceComplete<AdvancedPage, std::true_type>,
                                    // method 'statusMessage'
                                    QtPrivate::TypeAndForceComplete<void, std::false_type>,
                                    QtPrivate::TypeAndForceComplete<const QString&, std::false_type>,
                                    // method 'onBrowseSource'
                                    QtPrivate::TypeAndForceComplete<void, std::false_type>,
                                    // method 'onBrowseOutput'
                                    QtPrivate::TypeAndForceComplete<void, std::false_type>,
                                    // method 'onBuildPreview'
                                    QtPrivate::TypeAndForceComplete<void, std::false_type>,
                                    // method 'onRun'
                                    QtPrivate::TypeAndForceComplete<void, std::false_type>,
                                    // method 'onCancel'
                                    QtPrivate::TypeAndForceComplete<void, std::false_type>,
                                    // method 'onProcessFinished'
                                    QtPrivate::TypeAndForceComplete<void, std::false_type>,
                                    QtPrivate::TypeAndForceComplete<int, std::false_type>,
                                    QtPrivate::TypeAndForceComplete<QProcess::ExitStatus, std::false_type>
        >,
        nullptr
    }
};

void AdvancedPage::qt_static_metacall(QObject* _o, QMetaObject::Call _c, int _id, void** _a)
{
    auto* _t = static_cast<AdvancedPage*>(_o);
    if (_c == QMetaObject::InvokeMetaMethod)
    {
        switch (_id)
        {
        case 0: _t->statusMessage((*reinterpret_cast<std::add_pointer_t<QString>>(_a[1])));
            break;
        case 1: _t->onBrowseSource();
            break;
        case 2: _t->onBrowseOutput();
            break;
        case 3: _t->onBuildPreview();
            break;
        case 4: _t->onRun();
            break;
        case 5: _t->onCancel();
            break;
        case 6: _t->onProcessFinished((*reinterpret_cast<std::add_pointer_t<int>>(_a[1])),
                                      (*reinterpret_cast<std::add_pointer_t<QProcess::ExitStatus>>(_a[2])));
            break;
        default: ;
        }
    }
    if (_c == QMetaObject::IndexOfMethod)
    {
        int* result = reinterpret_cast<int*>(_a[0]);
        {
            using _q_method_type = void (AdvancedPage::*)(const QString&);
            if (_q_method_type _q_method = &AdvancedPage::statusMessage; *reinterpret_cast<_q_method_type*>(_a[1]) ==
                _q_method)
            {
                *result = 0;
                return;
            }
        }
    }
}

const QMetaObject* AdvancedPage::metaObject() const
{
    return QObject::d_ptr->metaObject ? QObject::d_ptr->dynamicMetaObject() : &staticMetaObject;
}

void* AdvancedPage::qt_metacast(const char* _clname)
{
    if (!_clname) return nullptr;
    if (!strcmp(_clname, qt_meta_stringdata_ZN12AdvancedPageE.stringdata0))
        return static_cast<void*>(this);
    return QWidget::qt_metacast(_clname);
}

int AdvancedPage::qt_metacall(QMetaObject::Call _c, int _id, void** _a)
{
    _id = QWidget::qt_metacall(_c, _id, _a);
    if (_id < 0)
        return _id;
    if (_c == QMetaObject::InvokeMetaMethod)
    {
        if (_id < 7)
            qt_static_metacall(this, _c, _id, _a);
        _id -= 7;
    }
    if (_c == QMetaObject::RegisterMethodArgumentMetaType)
    {
        if (_id < 7)
            *reinterpret_cast<QMetaType*>(_a[0]) = QMetaType();
        _id -= 7;
    }
    return _id;
}

// SIGNAL 0
void AdvancedPage::statusMessage(const QString& _t1)
{
    void* _a[] = {nullptr, const_cast<void*>(reinterpret_cast<const void*>(std::addressof(_t1)))};
    QMetaObject::activate(this, &staticMetaObject, 0, _a);
}

QT_WARNING_POP
