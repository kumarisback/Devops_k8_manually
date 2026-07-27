import React, { useState, useEffect } from 'react';
import { useAuth } from '../context/AuthContext';
import { Plus, Trash2, Edit2, LogOut, CheckCircle2, Circle, Clock, Check, X, AlertCircle } from 'lucide-react';

const Dashboard = () => {
    const { user, logout, authFetch } = useAuth();
    const [tasks, setTasks] = useState([]);
    const [title, setTitle] = useState('');
    const [description, setDescription] = useState('');
    const [status, setStatus] = useState('PENDING');
    const [editingTaskId, setEditingTaskId] = useState(null);
    const [editTitle, setEditTitle] = useState('');
    const [editDescription, setEditDescription] = useState('');
    const [editStatus, setEditStatus] = useState('PENDING');
    const [error, setError] = useState('');
    const [loading, setLoading] = useState(true);

    const loadTasks = async () => {
        try {
            setLoading(true);
            const response = await authFetch('/api/tasks');
            const data = await response.json();
            if (response.ok) {
                setTasks(data);
            } else {
                setError('Failed to fetch tasks');
            }
        } catch (err) {
            setError(err.message || 'Error fetching tasks');
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        loadTasks();
    }, []);

    const handleCreateTask = async (e) => {
        e.preventDefault();
        setError('');

        if (!title.trim()) {
            setError('Task title is required');
            return;
        }

        try {
            const response = await authFetch('/api/tasks', {
                method: 'POST',
                body: JSON.stringify({ title, description, status }),
            });

            if (response.ok) {
                setTitle('');
                setDescription('');
                setStatus('PENDING');
                loadTasks();
            } else {
                const msg = await response.text();
                setError(msg || 'Failed to create task');
            }
        } catch (err) {
            setError(err.message || 'Error creating task');
        }
    };

    const handleDeleteTask = async (id) => {
        if (!window.confirm('Are you sure you want to delete this task?')) return;
        setError('');
        try {
            const response = await authFetch(`/api/tasks/${id}`, {
                method: 'DELETE',
            });

            if (response.ok) {
                loadTasks();
            } else {
                const msg = await response.text();
                setError(msg || 'Failed to delete task');
            }
        } catch (err) {
            setError(err.message || 'Error deleting task');
        }
    };

    const startEdit = (task) => {
        setEditingTaskId(task.id);
        setEditTitle(task.title);
        setEditDescription(task.description || '');
        setEditStatus(task.status);
    };

    const cancelEdit = () => {
        setEditingTaskId(null);
    };

    const handleUpdateTask = async (e) => {
        e.preventDefault();
        setError('');

        if (!editTitle.trim()) {
            setError('Task title is required for update');
            return;
        }

        try {
            const response = await authFetch(`/api/tasks/${editingTaskId}`, {
                method: 'PUT',
                body: JSON.stringify({ title: editTitle, description: editDescription, status: editStatus }),
            });

            if (response.ok) {
                setEditingTaskId(null);
                loadTasks();
            } else {
                const msg = await response.text();
                setError(msg || 'Failed to update task');
            }
        } catch (err) {
            setError(err.message || 'Error updating task');
        }
    };

    const toggleTaskStatus = async (task) => {
        const nextStatus = task.status === 'COMPLETED' ? 'PENDING' : 'COMPLETED';
        try {
            const response = await authFetch(`/api/tasks/${task.id}`, {
                method: 'PUT',
                body: JSON.stringify({ 
                    title: task.title, 
                    description: task.description, 
                    status: nextStatus 
                }),
            });

            if (response.ok) {
                loadTasks();
            } else {
                const msg = await response.text();
                setError(msg || 'Failed to toggle task');
            }
        } catch (err) {
            setError(err.message || 'Error toggling status');
        }
    };

    const getStatusIcon = (statusStr) => {
        switch (statusStr) {
            case 'COMPLETED':
                return <CheckCircle2 className="status-icon text-success" size={20} />;
            case 'IN_PROGRESS':
                return <Clock className="status-icon text-warning" size={20} />;
            default:
                return <Circle className="status-icon text-muted" size={20} />;
        }
    };

    return (
        <div className="dashboard-container">
            <header className="dashboard-header">
                <div className="header-brand">
                    <h1>TaskSpace</h1>
                    <span className="user-badge">Logged in as: <strong>{user?.username}</strong></span>
                </div>
                <button onClick={logout} className="btn-logout" title="Sign Out">
                    <LogOut size={18} />
                    <span>Sign Out</span>
                </button>
            </header>

            <main className="dashboard-content">
                {error && (
                    <div className="alert alert-danger mb-4">
                        <AlertCircle size={18} />
                        <span>{error}</span>
                    </div>
                )}

                <div className="dashboard-grid">
                    {/* Create Task Panel */}
                    <section className="task-form-panel">
                        <h2>{editingTaskId ? 'Edit Task' : 'Create New Task'}</h2>
                        
                        {editingTaskId ? (
                            <form onSubmit={handleUpdateTask} className="task-form">
                                <div className="input-group">
                                    <label>Title</label>
                                    <input
                                        type="text"
                                        value={editTitle}
                                        onChange={(e) => setEditTitle(e.target.value)}
                                        placeholder="What needs to be done?"
                                        required
                                    />
                                </div>
                                <div className="input-group">
                                    <label>Description</label>
                                    <textarea
                                        value={editDescription}
                                        onChange={(e) => setEditDescription(e.target.value)}
                                        placeholder="Add more details (optional)"
                                        rows={3}
                                    />
                                </div>
                                <div className="input-group">
                                    <label>Status</label>
                                    <select value={editStatus} onChange={(e) => setEditStatus(e.target.value)}>
                                        <option value="PENDING">Pending</option>
                                        <option value="IN_PROGRESS">In Progress</option>
                                        <option value="COMPLETED">Completed</option>
                                    </select>
                                </div>
                                <div className="form-actions">
                                    <button type="submit" className="btn btn-primary flex-1">Save Changes</button>
                                    <button type="button" onClick={cancelEdit} className="btn btn-secondary">Cancel</button>
                                </div>
                            </form>
                        ) : (
                            <form onSubmit={handleCreateTask} className="task-form">
                                <div className="input-group">
                                    <label>Title</label>
                                    <input
                                        type="text"
                                        value={title}
                                        onChange={(e) => setTitle(e.target.value)}
                                        placeholder="What needs to be done?"
                                        required
                                    />
                                </div>
                                <div className="input-group">
                                    <label>Description</label>
                                    <textarea
                                        value={description}
                                        onChange={(e) => setDescription(e.target.value)}
                                        placeholder="Add more details (optional)"
                                        rows={3}
                                    />
                                </div>
                                <div className="input-group">
                                    <label>Status</label>
                                    <select value={status} onChange={(e) => setStatus(e.target.value)}>
                                        <option value="PENDING">Pending</option>
                                        <option value="IN_PROGRESS">In Progress</option>
                                        <option value="COMPLETED">Completed</option>
                                    </select>
                                </div>
                                <button type="submit" className="btn btn-primary w-full">
                                    <Plus size={18} />
                                    <span>Add Task</span>
                                </button>
                            </form>
                        )}
                    </section>

                    {/* Task List Panel */}
                    <section className="task-list-panel">
                        <div className="panel-header">
                            <h2>My Tasks ({tasks.length})</h2>
                            <button onClick={loadTasks} className="btn-refresh">Refresh</button>
                        </div>

                        {loading ? (
                            <div className="loader-box">
                                <div className="spinner"></div>
                                <p>Fetching tasks...</p>
                            </div>
                        ) : tasks.length === 0 ? (
                            <div className="empty-state">
                                <p>No tasks yet. Create one on the left to get started!</p>
                            </div>
                        ) : (
                            <div className="task-cards-list">
                                {tasks.map((task) => (
                                    <div key={task.id} className={`task-card ${task.status.toLowerCase()}`}>
                                        <div className="task-card-header">
                                            <button 
                                                className="status-toggle-btn"
                                                onClick={() => toggleTaskStatus(task)}
                                                title="Toggle status"
                                            >
                                                {getStatusIcon(task.status)}
                                            </button>
                                            <div className="task-info">
                                                <h3 className={task.status === 'COMPLETED' ? 'completed-text' : ''}>
                                                    {task.title}
                                                </h3>
                                                {task.description && (
                                                    <p className="task-desc">{task.description}</p>
                                                )}
                                                <span className={`status-badge ${task.status.toLowerCase()}`}>
                                                    {task.status.replace('_', ' ')}
                                                </span>
                                            </div>
                                        </div>
                                        <div className="task-card-actions">
                                            <button 
                                                onClick={() => startEdit(task)} 
                                                className="icon-btn edit-btn" 
                                                title="Edit"
                                                disabled={editingTaskId === task.id}
                                            >
                                                <Edit2 size={16} />
                                            </button>
                                            <button 
                                                onClick={() => handleDeleteTask(task.id)} 
                                                className="icon-btn delete-btn" 
                                                title="Delete"
                                            >
                                                <Trash2 size={16} />
                                            </button>
                                        </div>
                                    </div>
                                ))}
                            </div>
                        )}
                    </section>
                </div>
            </main>
        </div>
    );
};

export default Dashboard;
