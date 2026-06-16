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
  const [searchBloodType, setSearchBloodType] = useState('O+')
  const [searchResults, setSearchResults] = useState(null)
  const [selectedNode, setSelectedNode] = useState(1)
  const [localNodeId, setLocalNodeId] = useState(null)
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
      if (state.localNodeId) {
        setLocalNodeId(state.localNodeId)
      }
    })

    socket.on('logs', (initialLogs) => {
      setLogs(initialLogs)
    })

    socket.on('log', (logEntry) => {
      setLogs(prev => [logEntry, ...prev].slice(0, 50))
    })

    socket.on('searchResult', (results) => {
      setSearchResults(results)
    })

    return () => {
      socket.off('state')
      socket.off('logs')
      socket.off('log')
      socket.off('searchResult')
    }
  }, [])

  const handleAddDonor = (e) => {
    e.preventDefault()
    if (!newDonorName) return
    socket.emit('addDonor', { nodeId: Number(selectedNode), name: newDonorName, bloodType: newDonorType })
    setNewDonorName('')
  }

  const handleSearchDonor = (e) => {
    e.preventDefault()
    socket.emit('searchDonor', { bloodType: searchBloodType })
  }

  const coordIds = nodes.filter(n => n.state === 'active' && n.coordinator === n.id).map(n => n.id)
  const actualCoordId = coordIds.length > 0 ? Math.max(...coordIds) : -1

  return (
    <div className="app-container">
      <header className="header">
        <div className="header-left">
          <h1 className="title">Sistema Distribuido Hospitalario</h1>
          <p className="subtitle">🏥 Bully | 🕐 Cristian | 🔄 Relojes Vectoriales</p>
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
      <div style={{ display: 'flex', gap: '20px', flexWrap: 'wrap' }}>
        <section className="glass-panel donor-section" style={{ flex: '1' }}>
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
            <select 
              value={selectedNode}
              onChange={e => setSelectedNode(e.target.value)}
              className="input-field select-field"
            >
              {nodes.filter(n => n.state === 'active' && n.id !== localNodeId).map(n => (
                <option key={n.id} value={n.id}>Destino: Nodo {n.id}</option>
              ))}
            </select>
            <button type="submit" className="btn-primary">➕ Agregar</button>
          </form>
        </section>

        {/* Search Coordinated Section */}
        <section className="glass-panel donor-section" style={{ flex: '1' }}>
          <h2>🔍 Búsqueda Coordinada</h2>
          <form onSubmit={handleSearchDonor} className="donor-form-inline">
            <select 
              value={searchBloodType}
              onChange={e => setSearchBloodType(e.target.value)}
              className="input-field select-field"
            >
              <option>O+</option><option>O-</option>
              <option>A+</option><option>A-</option>
              <option>B+</option><option>B-</option>
              <option>AB+</option><option>AB-</option>
            </select>
            <button type="submit" className="btn-primary" style={{ backgroundColor: '#4CAF50' }}>Buscar (Vía Coordinador)</button>
          </form>
          {searchResults && (
            <div style={{ marginTop: '10px', fontSize: '0.9em' }}>
              <strong>Resultados ({searchResults.length}):</strong>
              <ul style={{ paddingLeft: '20px', marginTop: '5px' }}>
                {searchResults.map((d, i) => (
                  <li key={i}>{d.name} ({d.bloodType}) - Origen: Nodo {d.nodeOrigin}</li>
                ))}
              </ul>
            </div>
          )}
        </section>
      </div>

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
                      <td className="coord-value">
                        {actualCoordId === -1 ? 'Ninguno' : `${actualCoordId} ${node.id === actualCoordId ? '👑' : ''}`}
                      </td>
                      <td className="clock-value">
                        {(() => {
                          const d = new Date(node.clock);
                          return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}:${String(d.getSeconds()).padStart(2, '0')}.${String(d.getMilliseconds()).padStart(3, '0')}`;
                        })()}
                      </td>
                      <td className="vc-value">[{node.vectorClock.join(', ')}]</td>
                      <td className="donors-value">{node.donorsCount}</td>
                      <td className="action-cell">
                        {node.state === 'active' ? '🟢 Conectado' : '🔴 Desconectado'}
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