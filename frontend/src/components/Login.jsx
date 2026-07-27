import React, { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { LogIn, Lock, User, AlertCircle } from 'lucide-react';

const Login = () => {
    const [username, setUsername] = useState('');
    const [password, setPassword] = useState('');
    const [error, setError] = useState('');
    const [loading, setLoading] = useState(false);
    const { login } = useAuth();
    const navigate = useNavigate();

    const handleSubmit = async (e) => {
        e.preventDefault();
        setError('');

        if (!username || !password) {
            setError('Please fill in all fields');
            return;
        }

        setLoading(true);
        const result = await login(username, password);
        setLoading(false);

        if (result.success) {
            navigate('/');
        } else {
            setError(result.error || 'Invalid username or password');
        }
    };

    return (
        <div className="auth-card">
            <div className="auth-header">
                <div className="logo-circle">
                    <LogIn size={28} />
                </div>
                <h2>Welcome Back</h2>
                <p>Sign in to your account to access tasks</p>
            </div>

            <form onSubmit={handleSubmit} className="auth-form">
                {error && (
                    <div className="alert alert-danger">
                        <AlertCircle size={18} />
                        <span>{error}</span>
                    </div>
                )}

                <div className="input-group">
                    <label htmlFor="username">Username</label>
                    <div className="input-wrapper">
                        <User className="input-icon" size={18} />
                        <input
                            type="text"
                            id="username"
                            value={username}
                            onChange={(e) => setUsername(e.target.value)}
                            placeholder="Enter username"
                            required
                        />
                    </div>
                </div>

                <div className="input-group">
                    <label htmlFor="password">Password</label>
                    <div className="input-wrapper">
                        <Lock className="input-icon" size={18} />
                        <input
                            type="password"
                            id="password"
                            value={password}
                            onChange={(e) => setPassword(e.target.value)}
                            placeholder="Enter password"
                            required
                        />
                    </div>
                </div>

                <button type="submit" className="btn btn-primary" disabled={loading}>
                    {loading ? 'Signing In...' : 'Sign In'}
                </button>
            </form>

            <div className="auth-footer">
                Don't have an account? <Link to="/register">Sign Up</Link>
            </div>
        </div>
    );
};

export default Login;
