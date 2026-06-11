import { useEffect, useState } from 'react'
import io from 'socket.io-client'
import './index.css'

const socket = io(`http://${window.location.hostname}:3001`, {
  transports: ['websocket']
});

function App() {
  const [nodes, setNodes] = useState([])
  const [logs, setLogs] = useState([])
  const [newDonorName, setNewDonorName] = useState('')
  const [newDonorType, setNewDonorType] = useState('O+')
  const [selectedNode, setSelectedNode] = useState(1)

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
        <h1 className="title">Hospital Distributed System</h1>
        <p className="subtitle">Bully | Berkeley | Vector Clock</p>
      </header>

      <div className="main-grid">
        <div className="left-panel">
          <section className="glass-panel">
            <h2>Network Nodes</h2>
            <div className="nodes-grid">
              {nodes.map(node => (
                <div key={node.id} className={`node-card ${node.state === 'active' ? 'active' : 'failed'}`}>
                  <div className="node-header">
                    <h3>Node {node.id}</h3>
                    <span className="status-badge">{node.state.toUpperCase()}</span>
                  </div>
                  
                  <div className="node-details">
                    <p><span>Coord:</span> {node.coordinator === -1 ? 'None' : node.coordinator} {node.coordinator === node.id && '👑'}</p>
                    <p><span>Clock:</span> {new Date(node.clock).toISOString().substr(11, 12)}</p>
                    <p><span>VC:</span> [{node.vectorClock.join(', ')}]</p>
                    <p><span>Donors:</span> {node.donorsCount}</p>
                  </div>

                  <div className="node-actions">
                    {node.state === 'active' ? (
                      <button onClick={() => handleKill(node.id)} className="btn-fail">Fail Node</button>
                    ) : (
                      <button onClick={() => handleRecover(node.id)} className="btn-recover">Recover Node</button>
                    )}
                  </div>
                </div>
              ))}
            </div>
          </section>

          <section className="glass-panel mt-2">
            <h2>Add Donor Operation</h2>
            <form onSubmit={handleAddDonor} className="donor-form">
              <input 
                type="text" 
                value={newDonorName}
                onChange={e => setNewDonorName(e.target.value)}
                placeholder="Donor Name" 
                className="input-field"
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
                  <option key={n.id} value={n.id}>via Node {n.id}</option>
                ))}
              </select>
              <button type="submit" className="btn-primary">Add Donor</button>
            </form>
          </section>
        </div>

        <div className="right-panel glass-panel">
          <h2 className="logs-title">System Logs <span className="pulsing-dot"></span></h2>
          <div className="logs-container custom-scrollbar">
            {logs.map((log, i) => (
              <div key={i} className="log-entry">
                {log}
              </div>
            ))}
          </div>
        </div>
      </div>
    </div>
  )
}

export default App
