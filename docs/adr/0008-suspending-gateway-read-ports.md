# Suspending gateway read ports and the run-event observation shape

Status: accepted (2026-11-25)

The mutating store ports became `suspend` under #401, but every member of `SessionGatewayPort` and
`RunGatewayPort` stayed a blocking `fun`, so a Session read and a Run observation could not yield a
thread. A test double cannot gate a blocking `fun` with a `CompletableDeferred` or a `Channel`
without blocking the thread that carries it, so #452 cannot remove its real-time waits while this
shape stands. The state holder already carries these calls on its own IO scope, and it wrapped the
submit, observe and recover calls in `runInterruptible` only to keep cancellation working.

Decision: every member of both ports is `suspend`, including the default `createRun(request)`
overload and every use case that forwards to them. The production adapter keeps its blocking
transport and runs each call in `runInterruptible` on the caller's lane, so a cancelled call still
interrupts the blocked thread and `withTimeout` still fires; no production call site uses
`runBlocking`, and the state holder no longer wraps a port call in `runInterruptible` because the
port itself now suspends. The state holder drives the ports from its own scope, never from the
state-change lane.

Decision: `RunEventObservation` stays a one-shot pull stream, and its `Sequence` supertype becomes
`suspend fun next(): RunEvent?`. The consumer applies one event at a time and stops at the terminal
state, and another coroutine may close the stream, so a pull accessor keeps the existing ownership
model: the holder owns the job, the observation and the close, and it breaks out of the drain when
the state reached its terminal value. A `Flow` would replace that with a collector coroutine and
move the open/close boundary into the flow builder, and it would add a coroutines dependency to the
domain module, which declares none today.

Considered options: converting the observation to `Flow<RunEvent>` (rejected: a collector-coroutine
ownership model duplicates the job ownership the holder already has, and the domain module would
gain a coroutines dependency); keeping the `Sequence` and adding a suspend accessor next to it
(rejected: two accessors describe one stream, and the blocking one stays available to a caller that
must not use it); keeping the blocking members (rejected: it is the shape that blocks #452).

Consequences: a double gates any port call or `next()` with a deferred or a channel, and a
`StandardTestDispatcher` suspends inside the call instead of parking a thread. A suspend port also
compiles to a coroutine state machine whose branches only a suspending caller reaches, so PIT now
mutates that plumbing beside the hand-written logic and the module's measured score moves; the
declared mutation score of `:feature:entry:data` is re-measured in this change, following
[Quality gates](../quality-gates.md#changing-a-declared-value). A blocking implementation is still
valid when it is interruptible, so a double that blocks in an interruptible gate keeps its previous
cancellation behavior; a double that blocks without it cannot be stopped by a caller timeout. An
observation is single-consumer: one instance serves one drain, so a test that restarts an
observation hands out a fresh instance.
