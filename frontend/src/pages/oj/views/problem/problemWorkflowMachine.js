import { createMachine, interpret, assign } from 'xstate'

const DEFAULT_CONTEXT = {
  phase: 'READING',
  planPaused: false,
  runtimeState: null
}

const RUNNING_LIFECYCLE_STATES = new Set(['ws_connecting', 'running', 'restoring'])

function normalizePhase(phase) {
  return String(phase || 'READING').toUpperCase()
}

function normalizeRuntimeState(runtimeState) {
  return runtimeState || null
}

const assignPhase = assign((context, event) => ({
  ...context,
  phase: normalizePhase(event.phase || context.phase)
}))

const assignRuntimeState = assign((context, event) => ({
  ...context,
  runtimeState: normalizeRuntimeState(event.runtimeState || context.runtimeState)
}))

const assignPlanState = assign((context, event) => ({
  ...context,
  planPaused: event.planState === 'plan_paused' || !!event.planPaused
}))

const assignSnapshot = assign((context, event) => ({
  ...context,
  phase: normalizePhase(event.phase || context.phase),
  planPaused: event.planState === 'plan_paused' || !!event.planPaused,
  runtimeState: normalizeRuntimeState(event.runtimeState || context.runtimeState)
}))

const problemWorkflowMachine = createMachine({
  id: 'problemWorkflow',
  predictableActionArguments: true,
  type: 'parallel',
  context: DEFAULT_CONTEXT,
  states: {
    lifecycle: {
      initial: 'session_bootstrap',
      states: {
        session_bootstrap: {
          on: lifecycleEventMap()
        },
        ws_connecting: {
          on: lifecycleEventMap()
        },
        ready: {
          on: lifecycleEventMap()
        },
        running: {
          on: lifecycleEventMap()
        },
        waiting_human_approval: {
          on: lifecycleEventMap()
        },
        restoring: {
          on: lifecycleEventMap()
        },
        failed: {
          on: lifecycleEventMap()
        },
        cleared: {
          on: lifecycleEventMap()
        }
      }
    },
    plan: {
      initial: 'idle',
      states: {
        idle: {
          on: planEventMap()
        },
        plan_active: {
          on: planEventMap()
        },
        plan_paused: {
          on: planEventMap()
        },
        plan_completed: {
          on: planEventMap()
        }
      }
    }
  },
  on: {
    PHASE_CHANGE: {
      actions: assignPhase
    },
    SYNC_SNAPSHOT: {
      actions: assignSnapshot
    },
    RUNTIME_EVENT: {
      actions: assignRuntimeState
    },
    PLAN_SYNC: {
      actions: assignPlanState
    },
    RESET: {
      actions: assign(() => ({ ...DEFAULT_CONTEXT }))
    },
    CLEAR: {
      actions: assign(() => ({ ...DEFAULT_CONTEXT }))
    }
  }
})

function lifecycleEventMap() {
  return {
    SESSION_BOOTSTRAP: {
      target: 'session_bootstrap',
      actions: assignSnapshot
    },
    WS_CONNECTING: {
      target: 'ws_connecting',
      actions: assignSnapshot
    },
    RUN_REQUESTED: {
      target: 'running',
      actions: assignSnapshot
    },
    RUN_SETTLED: {
      target: 'ready',
      actions: assignSnapshot
    },
    APPROVAL_REQUESTED: {
      target: 'waiting_human_approval',
      actions: assignSnapshot
    },
    APPROVAL_RESOLVED: {
      target: 'ready',
      actions: assignSnapshot
    },
    RESTORE_STARTED: {
      target: 'restoring',
      actions: assignSnapshot
    },
    RESTORE_COMPLETED: {
      target: 'ready',
      actions: assignSnapshot
    },
    FAILED: {
      target: 'failed',
      actions: assignSnapshot
    },
    CLEAR: {
      target: 'cleared',
      actions: assign(() => ({ ...DEFAULT_CONTEXT }))
    },
    RESET: {
      target: 'session_bootstrap',
      actions: assign(() => ({ ...DEFAULT_CONTEXT }))
    },
    SYNC_SNAPSHOT: lifecycleSyncTransitions(assignSnapshot),
    RUNTIME_EVENT: lifecycleSyncTransitions(assignRuntimeState)
  }
}

function planEventMap() {
  return {
    PLAN_SYNC: planSyncTransitions(),
    RESET: {
      target: 'idle',
      actions: assign(() => ({ ...DEFAULT_CONTEXT }))
    },
    CLEAR: {
      target: 'idle',
      actions: assign(() => ({ ...DEFAULT_CONTEXT }))
    }
  }
}

function lifecycleSyncTransitions(actions) {
  return [
    {
      target: 'waiting_human_approval',
      cond: (_, event) => event.lifecycleState === 'waiting_human_approval',
      actions
    },
    {
      target: 'restoring',
      cond: (_, event) => event.lifecycleState === 'restoring',
      actions
    },
    {
      target: 'running',
      cond: (_, event) => event.lifecycleState === 'running',
      actions
    },
    {
      target: 'ws_connecting',
      cond: (_, event) => event.lifecycleState === 'ws_connecting',
      actions
    },
    {
      target: 'failed',
      cond: (_, event) => event.lifecycleState === 'failed',
      actions
    },
    {
      target: 'cleared',
      cond: (_, event) => event.lifecycleState === 'cleared',
      actions
    },
    {
      target: 'session_bootstrap',
      cond: (_, event) => event.lifecycleState === 'session_bootstrap',
      actions
    },
    {
      target: 'ready',
      actions
    }
  ]
}

function planSyncTransitions() {
  return [
    {
      target: 'plan_paused',
      cond: (_, event) => event.planState === 'plan_paused',
      actions: assignPlanState
    },
    {
      target: 'plan_completed',
      cond: (_, event) => event.planState === 'plan_completed',
      actions: assignPlanState
    },
    {
      target: 'plan_active',
      cond: (_, event) => event.planState === 'plan_active',
      actions: assignPlanState
    },
    {
      target: 'idle',
      actions: assignPlanState
    }
  ]
}

export function createProblemWorkflowService(initialContext = {}, onChange = null) {
  const service = interpret(problemWorkflowMachine.withContext({
    ...DEFAULT_CONTEXT,
    ...initialContext,
    phase: normalizePhase(initialContext.phase || DEFAULT_CONTEXT.phase),
    runtimeState: normalizeRuntimeState(initialContext.runtimeState || DEFAULT_CONTEXT.runtimeState)
  }))

  service.onTransition((state) => {
    if (!onChange || !state.changed) return
    const lifecycleState = state.value.lifecycle || 'session_bootstrap'
    const planState = state.value.plan || 'idle'
    onChange({
      phase: state.context.phase,
      planPaused: planState === 'plan_paused',
      planCompleted: planState === 'plan_completed',
      planActive: planState === 'plan_active',
      runtimeState: state.context.runtimeState,
      lifecycleState,
      planState,
      loading: RUNNING_LIFECYCLE_STATES.has(lifecycleState)
    })
  })

  service.start()
  const initialPlanState = initialContext.planCompleted
    ? 'plan_completed'
    : (initialContext.planPaused ? 'plan_paused' : (initialContext.planActive ? 'plan_active' : 'idle'))
  if (initialPlanState !== 'idle') {
    service.send({
      type: 'PLAN_SYNC',
      planState: initialPlanState,
      planPaused: initialPlanState === 'plan_paused'
    })
  }
  return service
}
