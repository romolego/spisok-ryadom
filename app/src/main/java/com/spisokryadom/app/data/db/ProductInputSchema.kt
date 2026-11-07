package com.spisokryadom.app.data.db

import androidx.sqlite.db.SupportSQLiteDatabase

internal object ProductInputSchema {
    fun create(db:SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS product_input_settings (id INTEGER NOT NULL PRIMARY KEY, prefillEnabled INTEGER NOT NULL, prefillCategory INTEGER NOT NULL, prefillUnit INTEGER NOT NULL, prefillDepartment INTEGER NOT NULL, inheritDepartment INTEGER NOT NULL, recipientEnabled INTEGER NOT NULL, recipientId INTEGER, FOREIGN KEY(recipientId) REFERENCES recipients(id) ON UPDATE NO ACTION ON DELETE SET NULL)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_product_input_settings_recipientId ON product_input_settings(recipientId)")
        db.execSQL("CREATE TABLE IF NOT EXISTS product_default_departments (productId INTEGER NOT NULL PRIMARY KEY, typeId INTEGER, FOREIGN KEY(productId) REFERENCES products(id) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(typeId) REFERENCES department_types(id) ON UPDATE NO ACTION ON DELETE SET NULL)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_product_default_departments_typeId ON product_default_departments(typeId)")
        db.execSQL("CREATE TABLE IF NOT EXISTS bulk_purchase_runs (token TEXT NOT NULL PRIMARY KEY, createdAt INTEGER NOT NULL)")
    }
}
