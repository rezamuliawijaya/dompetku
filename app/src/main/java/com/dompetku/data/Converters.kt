package com.dompetku.data

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun typeToString(type: TxType): String = type.name

    @TypeConverter
    fun stringToType(value: String): TxType = TxType.valueOf(value)

    @TypeConverter
    fun workspaceToString(workspace: Workspace): String = workspace.name

    @TypeConverter
    fun stringToWorkspace(value: String): Workspace = Workspace.valueOf(value)

    @TypeConverter
    fun kindToString(kind: AccountKind): String = kind.name

    @TypeConverter
    fun stringToKind(value: String): AccountKind = AccountKind.valueOf(value)

    @TypeConverter
    fun sourceToString(source: TxSource): String = source.name

    @TypeConverter
    fun stringToSource(value: String): TxSource = TxSource.valueOf(value)
}
