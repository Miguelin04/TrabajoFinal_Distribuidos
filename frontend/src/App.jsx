import { useEffect, useState } from 'react'
import io from 'socket.io-client'
import './App.css'

const socket = io(`http://${window.location.hostname}:3001`, {
  transports: ['websocket']
});

function App() {
  const [nodes, setNodes] = useState([])
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
          <h1 className="title">Hospital Distributed System</h1>
          <p className="subtitle">🏥 Bully | 🕐 Berkeley | 🔄 Vector Clock</p>
        </div>
        <div className="header-right">
          <div className="theme-toggle" onClick={toggleTheme}>
            <button className="theme-toggle-btn" title="Toggle theme">
              {isDarkTheme ? '☀️' : '🌙'}
            </button>
            <span className="theme-label">
              {isDarkTheme ? 'Dark' : 'Light'}
            </span>
          </div>
        </div>
      </header>

      {/* Add Donor Operation - Top Section */}
      <section className="glass-panel donor-section">
        <h2>💉 Add Donor Operation</h2>
        <form onSubmit={handleAddDonor} className="donor-form-inline">
          <input 
            type="text" 
            value={newDonorName}
            onChange={e => setNewDonorName(e.target.value)}
            placeholder="Donor Full Name" 
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
            {nodes.filter(n => n.state === 'active').map(n => (
              <option key={n.id} value={n.id}>Via Node {n.id}</option>
            ))}
          </select>
          <button type="submit" className="btn-primary">➕ Add Donor</button>
        </form>
      </section>

      <div className="main-grid">
        {/* Network Nodes Table */}
        <div className="left-panel">
          <section className="glass-panel">
            <h2>🌐 Network Nodes</h2>
            <div className="nodes-table-wrapper">
              <table className="nodes-table">
                <thead>
                  <tr>
                    <th>Node</th>
                    <th>Status</th>
                    <th>Coordinator</th>
                    <th>Clock</th>
                    <th>Vector Clock</th>
                    <th>Donors</th>
                    <th>Action</th>
                  </tr>
                </thead>
                <tbody>
                  {nodes.map(node => (
                    <tr key={node.id} className={`node-row ${node.state === 'active' ? 'active' : 'failed'}`}>
                      <td className="node-id">Node {node.id}</td>
                      <td>
                        <span className={`status-badge ${node.state}`}>
                          {node.state === 'active' ? '● Active' : '○ Failed'}
                        </span>
                      </td>
                      <td className="coord-value">
                        {node.coordinator === -1 ? 'None' : `${node.coordinator} ${node.coordinator === node.id ? '👑' : ''}`}
                      </td>
                      <td className="clock-value">{new Date(node.clock).toISOString().substr(11, 12)}</td>
                      <td className="vc-value">[{node.vectorClock.join(', ')}]</td>
                      <td className="donors-value">{node.donorsCount}</td>
                      <td className="action-cell">
                        {node.state === 'active' ? (
                          <button onClick={() => handleKill(node.id)} className="btn-fail btn-small">Fail</button>
                        ) : (
                          <button onClick={() => handleRecover(node.id)} className="btn-recover btn-small">Recover</button>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </section>
        </div>

        {/* System Logs */}
        <div className="right-panel glass-panel">
          <h2 className="logs-title">📋 System Logs <span className="pulsing-dot"></span></h2>
          <div className="logs-container">
            {logs.length === 0 ? (
              <div style={{textAlign: 'center', color: 'var(--text-tertiary)', padding: '2rem'}}>
                Waiting for system events...
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