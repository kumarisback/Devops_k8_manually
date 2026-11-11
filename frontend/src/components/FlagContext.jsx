import { useEffect } from 'react';
import { useUnleashContext } from '@unleash/proxy-client-react';
import { useAuth } from '../context/AuthContext';

// Tells Unleash who is signed in, so a gradual rollout gives each user the
// same answer every time (stickiness on userId) and a flag can target users
// by name (gitops docs/FEATURE-FLAGS.md).
const FlagContext = () => {
    const { user } = useAuth();
    const updateContext = useUnleashContext();

    useEffect(() => {
        updateContext(user
            ? { userId: String(user.id), properties: { username: user.username } }
            : { userId: undefined, properties: {} });
    }, [user, updateContext]);

    return null;
};

export default FlagContext;
