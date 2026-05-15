-- Script para crear la base de datos y la tabla de alertas
-- Ejecutar en el servidor PostgreSQL (192.168.10.203)
-- Usuario: svralertas

-- Crear la base de datos (solo si no existe)
-- CREATE DATABASE alertas_db;

-- Conectarse: \c alertas_db

-- Crear la tabla de alertas (estructura completa)
CREATE TABLE IF NOT EXISTS alerts (
    id SERIAL PRIMARY KEY,
    title TEXT NOT NULL,
    message TEXT NOT NULL,
    priority TEXT DEFAULT 'HIGH',
    time TEXT NOT NULL,
    date TEXT NOT NULL,
    timestamp BIGINT DEFAULT EXTRACT(EPOCH FROM NOW()) * 1000,
    created_at TIMESTAMP DEFAULT NOW()
);

-- Si la tabla ya existe y le faltan columnas, ejecuta estas líneas:
-- ALTER TABLE alerts ADD COLUMN priority TEXT DEFAULT 'HIGH';
 ALTER TABLE alerts ADD COLUMN created_at TIMESTAMP DEFAULT NOW();
