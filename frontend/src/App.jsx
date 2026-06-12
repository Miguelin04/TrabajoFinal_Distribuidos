import { useEffect, useState } from 'react'
import io from 'socket.io-client'
import './App.css'

const socket = io(`http://${window.location.hostname}:3001`, {
  transports: ['websocket']
});

function App() {
  const [nodes, setNodes] = useState([])
  const [donors, setDonors] = useState([])
  const [logs, setLogs] = useState([])
  const [newDonorName, setNewDonorName] = useState('')
  const [newDonorType, setNewDonorType] = useState('O+')
  const [selectedNode, setSelectedNode] = useState(1)
  const [isDarkTheme, setIsDarkTheme] = useState(true)

  // Cargar preferencia de tema desde localStorage
  useEffect(() => {
    const savedTheme = localStorage.getItem('theme')
    const prefersDark = savedTheme ? savedTheme === 'dark' : true
    setIsDarkTheme(prefersDark)
    applyTheme(prefersDark)
  }, [])

  // Aplicar tema al documento
  const applyTheme = (isDark) => {
    if (isDark) {
      document.documentElement.classList.remove('light-theme')
    } else {
      document.documentElement.classList.add('light-theme')
    }
    localStorage.setItem('theme', isDark ? 'dark' : 'light')
  }

  // Toggle tema
  const toggleTheme = () => {
    const newTheme = !isDarkTheme
    setIsDarkTheme(newTheme)
    applyTheme(newTheme)
  }

  useEffect(() => {
    socket.on('state', (state) => {
      setNodes(state.nodes)
      if (state.donors) {
        setDonors(state.donors)
      }
    })

    socket.on('logs', (initialLogs) => {
      setLogs(initialLogs)
    })

    socket.on('log', (logEntry) => {
      setLogs(prev => [logEntry, ...prev].slice(0, 50))
    })

    return () => {
      socket.off('state')
      socket.off('logs')
      socket.off('log')
    }
  }, [])

  const handleKill = (id) => socket.emit('killNode', id)
  const handleRecover = (id) => socket.emit('recoverNode', id)
  const handleAddDonor = (e) => {
    e.preventDefault()
    if (!newDonorName) return
    socket.emit('addDonor', { nodeId: Number(selectedNode), name: newDonorName, bloodType: newDonorType })
    setNewDonorName('')
  }

  return (
    <div className="app-container">
      <header className="header">
        <div className="header-left">
          <h1 className="title">Sistema Distribuido Hospitalario</h1>
          <p className="subtitle">🏥 Bully | 🕐 Berkeley | 🔄 Relojes Vectoriales</p>
        </div>
        <div className="header-right">
          <div className="theme-toggle" onClick={toggleTheme}>
            <button className="theme-toggle-btn" title="Toggle theme">
              {isDarkTheme ? '☀️' : '🌙'}
            </button>
            <span className="theme-label">
              {isDarkTheme ? 'Oscuro' : 'Claro'}
            </span>
          </div>
        </div>
      </header>

      {/* Add Donor Operation - Top Section */}
      <section className="glass-panel donor-section">
        <h2>💉 Agregar Donante</h2>
        <form onSubmit={handleAddDonor} className="donor-form-inline">
          <input 
            type="text" 
            value={newDonorName}
            onChange={e => setNewDonorName(e.target.value)}
            placeholder="Nombre Completo" 
            className="input-field"
            required
          />
          <select 
            value={newDonorType}
            onChange={e => setNewDonorType(e.target.value)}
            className="input-field select-field"
          >
            <option>O+</option><option>O-</option>
            <option>A+</option><option>A-</option>
            <option>B+</option><option>B-</option>
            <option>AB+</option><option>AB-</option>
          </select>
          <div className="input-field" style={{background: 'transparent', border: 'none', color: 'var(--text-secondary)'}}>
            (Se guardará en este hospital)
          </div>
          <button type="submit" className="btn-primary">➕ Agregar</button>
        </form>
      </section>

      <div className="main-grid">
        {/* Network Nodes Table */}
        <div className="left-panel">
          <section className="glass-panel">
            <h2>🌐 Nodos de la Red</h2>
            <div className="nodes-table-wrapper">
              <table className="nodes-table">
                <thead>
                  <tr>
                    <th>Nodo</th>
                    <th>Dirección IP</th>
                    <th>Estado</th>
                    <th>Coordinador</th>
                    <th>Reloj</th>
                    <th>Reloj Vectorial</th>
                    <th>Donantes</th>
                    <th>Acción</th>
                  </tr>
                </thead>
                <tbody>
                  {nodes.map(node => (
                    <tr key={node.id} className={`node-row ${node.state === 'active' ? 'active' : 'failed'}`}>
                      <td className="node-id">Nodo {node.id}</td>
                      <td className="node-ip" style={{fontSize: '0.85em', color: 'var(--text-secondary)'}}>{node.ip || 'Desconocida'}</td>
                      <td>
                        <span className={`status-badge ${node.state}`}>
                          {node.state === 'active' ? '● Activo' : '○ Caído'}
                        </span>
                      </td>
                      <td className="coord-value">
                        {node.coordinator === -1 ? 'Ninguno' : `${node.coordinator} ${node.coordinator === node.id ? '👑' : ''}`}
                      </td>
                      <td className="clock-value">{new Date(node.clock).toISOString().substr(11, 12)}</td>
                      <td className="vc-value">[{node.vectorClock.join(', ')}]</td>
                      <td className="donors-value">{node.donorsCount}</td>
                      <td className="action-cell">
                        {node.state === 'active' ? (
                          <button onClick={() => handleKill(node.id)} className="btn-fail btn-small">Tumbar</button>
                        ) : (
                          <button onClick={() => handleRecover(node.id)} className="btn-recover btn-small">Recuperar</button>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </section>

          {/* Donors Table Section */}
          <section className="glass-panel" style={{ marginTop: '20px' }}>
            <h2>🩸 Lista de Donantes (Ordenamiento Causal)</h2>
            <div className="nodes-table-wrapper">
              <table className="nodes-table">
                <thead>
                  <tr>
                    <th>Nombre</th>
                    <th>Tipo de Sangre</th>
                    <th>Añadido en Nodo</th>
                    <th>Reloj Vectorial del Evento</th>
                  </tr>
                </thead>
                <tbody>
                  {donors.length === 0 ? (
                    <tr>
                      <td colSpan="4" style={{textAlign: 'center', padding: '1rem'}}>Aún no hay donantes registrados.</td>
                    </tr>
                  ) : (
                    donors.map((donor, idx) => (
                      <tr key={idx}>
                        <td>{donor.name}</td>
                        <td>
                          <span className="blood-type-badge">{donor.bloodType}</span>
                        </td>
                        <td>Nodo {donor.nodeOrigin}</td>
                        <td className="vc-value">[{donor.vClock ? donor.vClock.join(', ') : '?'}]</td>
                      </tr>
                    ))
                  )}
                </tbody>
              </table>
            </div>
          </section>
        </div>

        {/* System Logs */}
        <div className="right-panel glass-panel">
          <h2 className="logs-title">📋 Registros del Sistema <span className="pulsing-dot"></span></h2>
          <div className="logs-container">
            {logs.length === 0 ? (
              <div style={{textAlign: 'center', color: 'var(--text-tertiary)', padding: '2rem'}}>
                Esperando eventos del sistema...
              </div>
            ) : (
              logs.map((log, i) => (
                <div key={i} className="log-entry">
                  {log}
                </div>
              ))
            )}
          </div>
        </div>
      </div>
    </div>
  )
}

export default App