const express = require('express');
const { Pool } = require('pg');
const cors = require('cors');

const app = express();
app.use(express.json());
app.use(cors());

// CONFIGURACIÓN DE TU POSTGRES
const pool = new Pool({
  user: 'tu_usuario',
  host: 'localhost',
  database: 'tu_base_de_datos',
  password: 'tu_password',
  port: 5432,
});

// Obtener todas las alertas
app.get('/alerts', async (req, res) => {
  try {
    const result = await pool.query('SELECT * FROM alerts ORDER BY created_at DESC');
    res.json(result.rows);
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// Guardar una nueva alerta
app.post('/alerts', async (req, res) => {
  const { title, message, priority, time, date } = req.body;
  try {
    const result = await pool.query(
      'INSERT INTO alerts (title, message, priority, time, date) VALUES ($1, $2, $3, $4, $5) RETURNING *',
      [title, message, priority, time, date]
    );
    res.json(result.rows[0]);
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

app.listen(3000, '0.0.0.0', () => {
  console.log('Servidor Cima listo en puerto 3000');
});
