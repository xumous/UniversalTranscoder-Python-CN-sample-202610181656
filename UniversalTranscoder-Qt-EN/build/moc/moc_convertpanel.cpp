/****************************************************************************
** Meta object code from reading C++ file 'convertpanel.h'
**
** Created by: The Qt Meta Object Compiler version 68 (Qt 6.8.1)
**
** WARNING! All changes made in this file will be lost!
*****************************************************************************/

#include "../../src/gui/convertpanel.h"
#include <QtCore/qmetatype.h>

#include <QtCore/qtmochelpers.h>

#include <memory>


#include <QtCore/qxptype_traits.h>
#if !defined(Q_MOC_OUTPUT_REVISION)
#error "The header file 'convertpanel.h' doesn't include <QObject>."
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
    struct qt_meta_tag_ZN12ConvertPanelE_t
    {
    };
} // unnamed namespace


#ifdef QT_MOC_HAS_STRINGDATA
static constexpr auto qt_meta_stringdata_ZN12ConvertPanelE = QtMocHelpers::stringData(
    "ConvertPanel",
    "statusMessage",
    "",
    "text",
    "onAddFiles",
    "onRemoveFiles",
    "onClearFiles",
    "onBrowseOutputDir",
    "onBrowseWatermark",
    "onBrowseSubtitle",
    "onPresetChanged",
    "index",
    "onStart",
    "onOpenOutputDir",
    "onExtractFrames",
    "onFrameProcessFinished",
    "exitCode",
    "QProcess::ExitStatus",
    "status"
);
#else  // !QT_MOC_HAS_STRINGDATA
#error "qtmochelpers.h not found or too old."
#endif // !QT_MOC_HAS_STRINGDATA

Q_CONSTINIT static const uint qt_meta_data_ZN12ConvertPanelE[] = {

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
    4, 0, 89, 2, 0x08, 3 /* Private */,
    5, 0, 90, 2, 0x08, 4 /* Private */,
    6, 0, 91, 2, 0x08, 5 /* Private */,
    7, 0, 92, 2, 0x08, 6 /* Private */,
    8, 0, 93, 2, 0x08, 7 /* Private */,
    9, 0, 94, 2, 0x08, 8 /* Private */,
    10, 1, 95, 2, 0x08, 9 /* Private */,
    12, 0, 98, 2, 0x08, 11 /* Private */,
    13, 0, 99, 2, 0x08, 12 /* Private */,
    14, 0, 100, 2, 0x08, 13 /* Private */,
    15, 2, 101, 2, 0x08, 14 /* Private */,

    // signals: parameters
    QMetaType::Void, QMetaType::QString, 3,

    // slots: parameters
    QMetaType::Void,
    QMetaType::Void,
    QMetaType::Void,
    QMetaType::Void,
    QMetaType::Void,
    QMetaType::Void,
    QMetaType::Void, QMetaType::Int, 11,
    QMetaType::Void,
    QMetaType::Void,
    QMetaType::Void,
    QMetaType::Void, QMetaType::Int, 0x80000000 | 17, 16, 18,

    0 // eod
};

Q_CONSTINIT const QMetaObject ConvertPanel::staticMetaObject = {
    {
        QMetaObject::SuperData::link<QWidget::staticMetaObject>(),
        qt_meta_stringdata_ZN12ConvertPanelE.offsetsAndSizes,
        qt_meta_data_ZN12ConvertPanelE,
        qt_static_metacall,
        nullptr,
        qt_incomplete_metaTypeArray<qt_meta_tag_ZN12ConvertPanelE_t,
                                    // Q_OBJECT / Q_GADGET
                                    QtPrivate::TypeAndForceComplete<ConvertPanel, std::true_type>,
                                    // method 'statusMessage'
                                    QtPrivate::TypeAndForceComplete<void, std::false_type>,
                                    QtPrivate::TypeAndForceComplete<const QString&, std::false_type>,
                                    // method 'onAddFiles'
                                    QtPrivate::TypeAndForceComplete<void, std::false_type>,
                                    // method 'onRemoveFiles'
                                    QtPrivate::TypeAndForceComplete<void, std::false_type>,
                                    // method 'onClearFiles'
                                    QtPrivate::TypeAndForceComplete<void, std::false_type>,
                                    // method 'onBrowseOutputDir'
                                    QtPrivate::TypeAndForceComplete<void, std::false_type>,
                                    // method 'onBrowseWatermark'
                                    QtPrivate::TypeAndForceComplete<void, std::false_type>,
                                    // method 'onBrowseSubtitle'
                                    QtPrivate::TypeAndForceComplete<void, std::false_type>,
                                    // method 'onPresetChanged'
                                    QtPrivate::TypeAndForceComplete<void, std::false_type>,
                                    QtPrivate::TypeAndForceComplete<int, std::false_type>,
                                    // method 'onStart'
                                    QtPrivate::TypeAndForceComplete<void, std::false_type>,
                                    // method 'onOpenOutputDir'
                                    QtPrivate::TypeAndForceComplete<void, std::false_type>,
                                    // method 'onExtractFrames'
                                    QtPrivate::TypeAndForceComplete<void, std::false_type>,
                                    // method 'onFrameProcessFinished'
                                    QtPrivate::TypeAndForceComplete<void, std::false_type>,
                                    QtPrivate::TypeAndForceComplete<int, std::false_type>,
                                    QtPrivate::TypeAndForceComplete<QProcess::ExitStatus, std::false_type>
        >,
        nullptr
    }
};

void ConvertPanel::qt_static_metacall(QObject* _o, QMetaObject::Call _c, int _id, void** _a)
{
    auto* _t = static_cast<ConvertPanel*>(_o);
    if (_c == QMetaObject::InvokeMetaMethod)
    {
        switch (_id)
        {
        case 0: _t->statusMessage((*reinterpret_cast<std::add_pointer_t<QString>>(_a[1])));
            break;
        case 1: _t->onAddFiles();
            break;
        case 2: _t->onRemoveFiles();
            break;
        case 3: _t->onClearFiles();
            break;
        case 4: _t->onBrowseOutputDir();
            break;
        case 5: _t->onBrowseWatermark();
            break;
        case 6: _t->onBrowseSubtitle();
            break;
        case 7: _t->onPresetChanged((*reinterpret_cast<std::add_pointer_t<int>>(_a[1])));
            break;
        case 8: _t->onStart();
            break;
        case 9: _t->onOpenOutputDir();
            break;
        case 10: _t->onExtractFrames();
            break;
        case 11: _t->onFrameProcessFinished((*reinterpret_cast<std::add_pointer_t<int>>(_a[1])),
                                            (*reinterpret_cast<std::add_pointer_t<QProcess::ExitStatus>>(_a[2])));
            break;
        default: ;
        }
    }
    if (_c == QMetaObject::IndexOfMethod)
    {
        int* result = reinterpret_cast<int*>(_a[0]);
        {
            using _q_method_type = void (ConvertPanel::*)(const QString&);
            if (_q_method_type _q_method = &ConvertPanel::statusMessage; *reinterpret_cast<_q_method_type*>(_a[1]) ==
                _q_method)
            {
                *result = 0;
                return;
            }
        }
    }
}

const QMetaObject* ConvertPanel::metaObject() const
{
    return QObject::d_ptr->metaObject ? QObject::d_ptr->dynamicMetaObject() : &staticMetaObject;
}

void* ConvertPanel::qt_metacast(const char* _clname)
{
    if (!_clname) return nullptr;
    if (!strcmp(_clname, qt_meta_stringdata_ZN12ConvertPanelE.stringdata0))
        return static_cast<void*>(this);
    return QWidget::qt_metacast(_clname);
}

int ConvertPanel::qt_metacall(QMetaObject::Call _c, int _id, void** _a)
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
void ConvertPanel::statusMessage(const QString& _t1)
{
    void* _a[] = {nullptr, const_cast<void*>(reinterpret_cast<const void*>(std::addressof(_t1)))};
    QMetaObject::activate(this, &staticMetaObject, 0, _a);
}

QT_WARNING_POP
