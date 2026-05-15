# 📢 Alertas CIMA - Tablets

Sistema de alertas en tiempo real para tablets Android en red local, con persistencia centralizada en base de datos PostgreSQL.

---

## 🏗️ Arquitectura del sistema

```
[Tablet Admin]
      │
      ├─── UDP Broadcast (LAN) ──────────► [Tablets Usuario] (muestran la alerta)
      │
      └─── HTTP POST ────────────────────► [Backend Node.js - VM 101 (.209:3000)]
                                                        │
                                                        └─── SQL INSERT ──► [PostgreSQL - VM 106 (.203:5432)]
```

| Componente        | IP / Puerto          | Descripción                              |
|-------------------|----------------------|------------------------------------------|
| Tablets Android   | Red local            | App cliente (Admin y Usuario)            |
| Backend Node.js   | `192.168.10.209:3000`| API REST intermediaria                   |
| Base de datos     | `192.168.10.203:5432`| PostgreSQL con la tabla `alerts`         |

---

## 📱 Aplicación Android

### Requisitos
- Android 8.0 (API 26) o superior
- Conexión a la red local (mismo segmento de red que el backend)
- Java 17 para compilar

### Compilar el APK

Desde el directorio raíz del proyecto en PowerShell:

```powershell
& "C:\Users\practicas.e5\.gradle\wrapper\dists\gradle-8.13-bin\5xuhj0ry160q40clulazy9h7d\gradle-8.13\bin\gradle.bat" assembleDebug
```

El APK generado se encontrará en:
```
app\build\outputs\apk\debug\app-debug.apk
```

### Instalar en las tablets
1. Copia el APK a la tablet (USB, red local, etc.)
2. Activa **"Instalar apps de fuentes desconocidas"** en los ajustes de la tablet
3. Abre el APK e instala
4. Concede el permiso de **superposición de pantalla** cuando se solicite

### Modos de uso
- **Modo Administrador**: Permite enviar alertas a todas las tablets de la red
- **Modo Usuario**: Escucha y muestra alertas en tiempo real como overlay de pantalla completa

---

## 🖥️ Backend (VM 101 - 192.168.10.209)

### Tecnología
- Node.js + Express
- PM2 para ejecución persistente

### Instalación
```bash
cd ~/apps/Alertas-CIMA-Tablets/cima-backend
npm install express pg cors
sudo pm2 start index.js --name "cima-backend"
sudo pm2 save
sudo pm2 startup
```

### Gestión del servicio
```bash
sudo pm2 status                    # Ver estado
sudo pm2 restart cima-backend      # Reiniciar
sudo pm2 logs cima-backend         # Ver logs en tiempo real
sudo pm2 stop cima-backend         # Parar
```

### Endpoints de la API

| Método | Ruta       | Descripción                        |
|--------|------------|------------------------------------|
| GET    | `/alerts`  | Obtiene todas las alertas guardadas |
| POST   | `/alerts`  | Guarda una nueva alerta             |

**Ejemplo POST:**
```bash
curl -X POST http://192.168.10.209:3000/alerts \
-H "Content-Type: application/json" \
-d '{"title":"Test","message":"Prueba manual","time":"10:00","date":"15/05/2026"}'
```

### Configuración de conexión a la base de datos (`index.js`)
```javascript
const pool = new Pool({
  user: 'svralertas',
  host: '192.168.10.203',
  database: 'alertas_db',
  password: 'TU_CONTRASEÑA',   // Contraseña configurada en el servidor .203
  port: 5432,
});
```

---

## 🗄️ Base de datos (VM 106 - 192.168.10.203)

### Tecnología
- PostgreSQL

### Acceso
```bash
sudo -u postgres psql -d alertas_db
```

### Estructura de la tabla `alerts`
```sql
CREATE TABLE alerts (
    id         SERIAL PRIMARY KEY,
    title      TEXT NOT NULL,
    message    TEXT NOT NULL,
    time       TEXT NOT NULL,
    date       TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT NOW()
);
```

### Permisos necesarios para el usuario `svralertas`
```sql
GRANT ALL PRIVILEGES ON TABLE alerts TO svralertas;
GRANT USAGE, SELECT ON SEQUENCE alerts_id_seq TO svralertas;
```

### Comandos útiles
```sql
-- Ver todas las alertas
SELECT * FROM alerts ORDER BY created_at DESC;

-- Ver alertas de hoy
SELECT * FROM alerts WHERE date = TO_CHAR(NOW(), 'DD/MM/YYYY');

-- Borrar todas las alertas (solo para pruebas)
DELETE FROM alerts;

-- Contar alertas totales
SELECT COUNT(*) FROM alerts;
```

### Configuración de red (`pg_hba.conf`)
La siguiente línea debe existir para permitir conexiones desde el backend:
```
host    all             all             192.168.10.209/32       md5
```

---

## 🔐 Seguridad
- La comunicación entre el backend y PostgreSQL está protegida por autenticación **MD5**
- El backend solo acepta conexiones desde la red local
- Las alertas UDP se envían en broadcast dentro del segmento de red local

---

## 🐛 Solución de problemas

| Problema | Causa probable | Solución |
|---|---|---|
| `0 rows` en la BD | Backend sin permisos o credenciales incorrectas | Verificar `index.js` y permisos en Postgres |
| `permission denied for table alerts` | Usuario sin privilegios | `GRANT ALL PRIVILEGES ON TABLE alerts TO svralertas;` |
| `password authentication failed` | Contraseña incorrecta en `index.js` | Editar `index.js` con la contraseña correcta y reiniciar PM2 |
| App no guarda en servidor | App no alcanza el backend | Verificar que el backend está corriendo y la IP es correcta |
| APK no compila | Falta `gradlew` | Usar la ruta completa al binario de Gradle (ver sección de compilación) |

---

## 📁 Estructura del proyecto

```
Alertas-CIMA-Tablets/
├── app/                          # Código Android
│   └── src/main/java/com/example/overlayapp/
│       ├── api/                  # Cliente HTTP (Retrofit)
│       ├── data/                 # Base de datos local (Room)
│       ├── model/                # Modelos de datos
│       ├── network/              # Comunicación LAN (UDP)
│       ├── service/              # Servicio de overlay
│       ├── ui/                   # Pantallas (Admin, Usuario, Login)
│       └── viewmodel/            # Lógica de negocio
├── cima-backend/                 # Backend Node.js
│   ├── index.js                  # Servidor Express + API
│   └── setup.sql                 # Script de inicialización de la BD
└── README.md                     # Este archivo
```
