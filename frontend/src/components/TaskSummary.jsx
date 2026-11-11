import React from 'react';

// How many tasks are in each state. Released behind the feature flag
// dashboard-task-summary (src/lib/flags.js): deployed to everyone, shown to
// whom the flag says.
const STATES = [
    ['PENDING', 'Pending'],
    ['IN_PROGRESS', 'In progress'],
    ['COMPLETED', 'Completed'],
];

const TaskSummary = ({ tasks }) => {
    const counts = Object.fromEntries(STATES.map(([state]) => [state, 0]));
    for (const task of tasks) {
        if (task.status in counts) {
            counts[task.status] += 1;
        }
    }
    return (
        <div className="task-summary" aria-label="Tasks by state">
            {STATES.map(([state, label]) => (
                <div key={state} className="task-summary-item">
                    <span className="task-summary-count">{counts[state]}</span>
                    <span className="task-summary-label">{label}</span>
                </div>
            ))}
        </div>
    );
};

export default TaskSummary;
