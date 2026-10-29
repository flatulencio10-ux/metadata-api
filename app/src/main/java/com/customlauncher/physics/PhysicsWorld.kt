package com.customlauncher.physics

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import com.customlauncher.core.model.BodyType
import com.customlauncher.core.model.ColliderShape
import com.customlauncher.core.model.GravityDirection
import com.customlauncher.core.model.Node
import com.customlauncher.core.model.PathPoint
import com.customlauncher.core.model.WorldConfig
import com.customlauncher.ui.edit.NodeBounds
import kotlinx.coroutines.channels.Channel
import org.jbox2d.callbacks.ContactImpulse
import org.jbox2d.callbacks.ContactListener
import org.jbox2d.collision.Manifold
import org.jbox2d.collision.shapes.CircleShape
import org.jbox2d.collision.shapes.EdgeShape
import org.jbox2d.collision.shapes.PolygonShape
import org.jbox2d.collision.shapes.Shape
import org.jbox2d.common.Vec2
import org.jbox2d.dynamics.Body
import org.jbox2d.dynamics.BodyDef
import org.jbox2d.dynamics.FixtureDef
import org.jbox2d.dynamics.World
import org.jbox2d.dynamics.contacts.Contact
import org.jbox2d.dynamics.joints.MouseJoint
import org.jbox2d.dynamics.joints.MouseJointDef
import org.jbox2d.dynamics.joints.RevoluteJointDef
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sqrt

/** Live pose of a simulated node, read by the renderer inside graphicsLayer (no recomposition). */
class BodyState(val id: String, x: Float, y: Float, rotation: Float) {
    /** Top-left in dp, like Node.x / Node.y. */
    var x by mutableFloatStateOf(x)
    var y by mutableFloatStateOf(y)
    /** Degrees. */
    var rotation by mutableFloatStateOf(rotation)
    /** Linear speed in dp/s and angular speed in rpm (for speed-reactive visuals). */
    var speed by mutableFloatStateOf(0f)
    var rpm by mutableFloatStateOf(0f)
    var vx = 0f
    var vy = 0f
    /** Total rotation in degrees since creation (for zoetrope frame selection). */
    var spin = 0f
}

/** Something hit something; ids are node ids, or null for walls / widgets. */
data class Collision(val a: String?, val b: String?, val impulse: Float)

/**
 * A page's physics simulation (JBox2D). Coordinates: dp on screen, 50 dp = 1 m, y down.
 * Bodies sleep when still; [step] then reports nothing awake and the caller stops looping.
 */
class PhysicsWorld(
    nodes: List<Node>,
    private val config: WorldConfig,
    private val pageWidth: Float,
    private val pageHeight: Float,
    maxDynamic: Int,
    /** Convex collider (node fractions) from the node's image alpha, if it has one. */
    hullOf: (Node) -> List<PathPoint>?,
    private val onCollision: (Collision) -> Unit = {},
) {
    private val world = World(Vec2(0f, 0f))
    private val ground: Body = world.createBody(BodyDef())
    private val bodies = HashMap<String, Body>()
    private val sizes = HashMap<String, Pair<Float, Float>>()
    val states = HashMap<String, BodyState>()
    private var mouseJoint: MouseJoint? = null
    private var draggedId: String? = null
    private var accumulator = 0f
    private var tilt = Vec2(0f, 0f)
    private val pendingContacts = HashSet<Contact>()

    /** Signalled when something needs the sleeping simulation to run again. */
    val wakeups = Channel<Unit>(Channel.CONFLATED)

    /** Milliseconds the last [step] took (performance overlay). */
    var lastStepMs = 0f
        private set
    val bodyCount get() = bodies.size
    val isDragging get() = mouseJoint != null

    init {
        applyGravity()
        if (config.wallLeft) wall(0f, 0f, 0f, pageHeight)
        if (config.wallRight) wall(pageWidth, 0f, pageWidth, pageHeight)
        if (config.wallTop) wall(0f, 0f, pageWidth, 0f)
        if (config.wallBottom) wall(0f, pageHeight, pageWidth, pageHeight)

        var dynamicCount = 0
        nodes.filter { !it.hidden }.sortedBy { it.z }.forEach { n ->
            val spec = n.physics
            when {
                spec != null && spec.type != BodyType.NONE -> {
                    var type = spec.type
                    if (type == BodyType.DYNAMIC && ++dynamicCount > maxDynamic) type = BodyType.STATIC
                    addBody(n, type, hullOf(n))
                }
                config.collideWithWidgets -> addObstacle(n)
            }
        }
        world.setContactListener(object : ContactListener {
            override fun beginContact(contact: Contact) { pendingContacts += contact }
            override fun endContact(contact: Contact) { pendingContacts -= contact }
            override fun preSolve(contact: Contact, oldManifold: Manifold) = Unit
            override fun postSolve(contact: Contact, impulse: ContactImpulse) {
                if (!pendingContacts.remove(contact)) return
                val strength = impulse.normalImpulses.take(impulse.count).maxOrNull() ?: 0f
                onCollision(Collision(contact.fixtureA.body.userData as? String, contact.fixtureB.body.userData as? String, strength))
            }
        })
    }

    private fun m(dp: Float) = dp / PPM

    private fun wall(x1: Float, y1: Float, x2: Float, y2: Float) {
        val edge = EdgeShape().apply { set(Vec2(m(x1), m(y1)), Vec2(m(x2), m(y2))) }
        ground.createFixture(FixtureDef().apply { shape = edge; friction = 0.4f })
    }

    private fun addObstacle(n: Node) {
        val b = NodeBounds.of(n)
        val body = world.createBody(BodyDef().apply {
            position.set(m(b.centerX), m(b.centerY))
        })
        val box = PolygonShape().apply { setAsBox(m((b.right - b.left) / 2f), m((b.bottom - b.top) / 2f)) }
        body.createFixture(FixtureDef().apply { shape = box; friction = 0.4f })
    }

    private fun addBody(n: Node, type: BodyType, hull: List<PathPoint>?) {
        val spec = n.physics ?: return
        val w = n.width * n.scale
        val h = n.height * n.scale
        val cx = n.x + n.width / 2f
        val cy = n.y + n.height / 2f
        val body = world.createBody(BodyDef().apply {
            this.type = when (type) {
                BodyType.DYNAMIC -> org.jbox2d.dynamics.BodyType.DYNAMIC
                BodyType.KINEMATIC -> org.jbox2d.dynamics.BodyType.KINEMATIC
                else -> org.jbox2d.dynamics.BodyType.STATIC
            }
            position.set(m(cx), m(cy))
            angle = (n.rotation * PI / 180.0).toFloat()
            linearDamping = spec.linearDamping
            angularDamping = spec.angularDamping
            gravityScale = spec.gravityScale
            fixedRotation = spec.fixedRotation
            bullet = type == BodyType.DYNAMIC
        })
        body.userData = n.id

        val shape: Shape
        val areaM2: Float
        val polygon = when (spec.collider) {
            ColliderShape.AUTO -> hull
            ColliderShape.POLYGON -> spec.polygon.takeIf { it.size >= 3 }
            else -> null
        }?.let { ColliderMath.simplify(it, ColliderMath.MAX_VERTICES) }
            ?.takeIf { ColliderMath.area(it) > 0.02f }

        when {
            spec.collider == ColliderShape.CIRCLE -> {
                shape = CircleShape().apply { m_radius = m(minOf(w, h) / 2f) }
                areaM2 = (PI * shape.m_radius * shape.m_radius).toFloat()
            }
            polygon != null -> {
                val flip = if (n.flipX) -1f else 1f
                val verts = polygon.map { Vec2(m((it.x - 0.5f) * w * flip), m((it.y - 0.5f) * h)) }.toTypedArray()
                shape = PolygonShape().apply { set(verts, verts.size) }
                areaM2 = ColliderMath.area(polygon) * m(w) * m(h)
            }
            else -> {
                shape = PolygonShape().apply { setAsBox(m(w / 2f), m(h / 2f)) }
                areaM2 = m(w) * m(h)
            }
        }
        body.createFixture(FixtureDef().apply {
            this.shape = shape
            density = spec.mass / max(areaM2, 0.01f)
            friction = spec.friction
            restitution = spec.restitution
        })

        if (spec.pinned) {
            val anchor = Vec2(m(n.x + n.width * spec.pinX), m(n.y + n.height * spec.pinY))
            world.createJoint(RevoluteJointDef().apply { initialize(ground, body, anchor) })
        }
        bodies[n.id] = body
        sizes[n.id] = n.width to n.height
        states[n.id] = BodyState(n.id, n.x, n.y, n.rotation)
    }

    private fun applyGravity() {
        val g = config.gravityStrength
        val v = if (config.followTilt) Vec2(tilt.x * g, tilt.y * g) else when (config.gravity) {
            GravityDirection.DOWN -> Vec2(0f, g)
            GravityDirection.UP -> Vec2(0f, -g)
            GravityDirection.LEFT -> Vec2(-g, 0f)
            GravityDirection.RIGHT -> Vec2(g, 0f)
            GravityDirection.NONE -> Vec2(0f, 0f)
        }
        world.gravity = v
    }

    /** Tilt in -1..1 per axis; wakes bodies when it changes noticeably. */
    fun setTilt(x: Float, y: Float) {
        if (!config.followTilt) return
        val changed = kotlin.math.abs(x - tilt.x) + kotlin.math.abs(y - tilt.y) > 0.05f
        tilt = Vec2(x, y)
        applyGravity()
        if (changed) wakeAll()
    }

    fun wakeAll() {
        bodies.values.forEach { it.isAwake = true }
        wakeups.trySend(Unit)
    }

    /**
     * Advances the simulation by [dt] seconds with fixed 60 Hz sub-steps.
     * @return true while anything is still moving (keep calling), false when all bodies sleep.
     */
    fun step(dt: Float): Boolean {
        val start = System.nanoTime()
        accumulator += dt.coerceAtMost(0.05f)
        while (accumulator >= STEP) {
            world.step(STEP, 8, 3)
            accumulator -= STEP
        }
        var awake = false
        bodies.forEach { (id, body) ->
            val st = states.getValue(id)
            val (w, h) = sizes.getValue(id)
            val p = body.position
            st.x = p.x * PPM - w / 2f
            st.y = p.y * PPM - h / 2f
            val deg = (body.angle * 180.0 / PI).toFloat()
            st.spin += deg - st.rotation
            st.rotation = deg
            val v = body.linearVelocity
            st.vx = v.x * PPM
            st.vy = v.y * PPM
            st.speed = sqrt(st.vx * st.vx + st.vy * st.vy)
            st.rpm = (body.angularVelocity * 60.0 / (2 * PI)).toFloat()
            if (body.isAwake && body.type != org.jbox2d.dynamics.BodyType.STATIC) awake = true
        }
        lastStepMs = (System.nanoTime() - start) / 1e6f
        return awake || mouseJoint != null
    }

    /** Topmost movable body under [x],[y] (dp), if any. */
    fun bodyAt(x: Float, y: Float): String? {
        val p = Vec2(m(x), m(y))
        return bodies.entries.lastOrNull { (_, body) ->
            body.type != org.jbox2d.dynamics.BodyType.STATIC &&
                generateSequence(body.fixtureList) { it.next }.any { it.testPoint(p) }
        }?.key
    }

    fun has(id: String) = id in bodies

    /** Grabs a body with a spring (mouse joint) so dragging feels physical. */
    fun startDrag(id: String, x: Float, y: Float) {
        val body = bodies[id] ?: return
        endDrag(0f, 0f, x, y, fling = false)
        if (body.type != org.jbox2d.dynamics.BodyType.DYNAMIC) return
        body.isAwake = true
        val def = MouseJointDef().apply {
            bodyA = ground
            bodyB = body
            target.set(m(x), m(y))
            maxForce = 1000f * body.mass
            frequencyHz = 6f
            dampingRatio = 0.7f
            collideConnected = true
        }
        mouseJoint = world.createJoint(def) as MouseJoint
        draggedId = id
        wakeups.trySend(Unit)
    }

    fun drag(x: Float, y: Float) {
        mouseJoint?.setTarget(Vec2(m(x), m(y)))
    }

    /**
     * Releases the body with the finger's velocity (dp/s): a throw. The tangential part of the
     * velocity around the body's centre becomes spin (flick-to-spin).
     */
    fun endDrag(vxDp: Float, vyDp: Float, x: Float, y: Float, fling: Boolean = true) {
        mouseJoint?.let { world.destroyJoint(it) }
        mouseJoint = null
        val body = draggedId?.let { bodies[it] }
        draggedId = null
        if (!fling || body == null) return
        val v = Vec2(m(vxDp), m(vyDp))
        val r = Vec2(m(x), m(y)).sub(body.worldCenter)
        val r2 = r.x * r.x + r.y * r.y
        val pinned = body.jointList != null
        if (!pinned) body.linearVelocity = v
        if (r2 > 1e-4f) body.angularVelocity = body.angularVelocity * 0.3f + (r.x * v.y - r.y * v.x) / r2
        body.isAwake = true
        wakeups.trySend(Unit)
    }

    /** Spins or pushes a body by the flick of a finger without grabbing it. */
    fun flick(id: String, vxDp: Float, vyDp: Float, x: Float, y: Float) {
        draggedId = id
        endDrag(vxDp, vyDp, x, y)
    }

    // ---- Used by behaviours and scripts ---------------------------------------------

    fun applyImpulse(id: String, ixDp: Float, iyDp: Float) {
        val b = bodies[id] ?: return
        b.applyLinearImpulse(Vec2(m(ixDp) * b.mass, m(iyDp) * b.mass), b.worldCenter)
        b.isAwake = true
        wakeups.trySend(Unit)
    }

    fun applyForce(id: String, fxDp: Float, fyDp: Float) {
        val b = bodies[id] ?: return
        b.applyForceToCenter(Vec2(m(fxDp) * b.mass, m(fyDp) * b.mass))
        b.isAwake = true
        wakeups.trySend(Unit)
    }

    fun applyTorque(id: String, rpmDelta: Float) {
        val b = bodies[id] ?: return
        b.angularVelocity = b.angularVelocity + (rpmDelta * 2 * PI / 60.0).toFloat()
        b.isAwake = true
        wakeups.trySend(Unit)
    }

    fun setVelocity(id: String, vxDp: Float, vyDp: Float) {
        val b = bodies[id] ?: return
        b.linearVelocity = Vec2(m(vxDp), m(vyDp))
        b.isAwake = true
        wakeups.trySend(Unit)
    }

    /** Turns simulation of one body off (it freezes in place) or back on. */
    fun setEnabled(id: String, enabled: Boolean) {
        val b = bodies[id] ?: return
        b.isActive = enabled
        if (enabled) { b.isAwake = true; wakeups.trySend(Unit) }
    }

    /** Teleports a body (top-left in dp). */
    fun moveTo(id: String, x: Float, y: Float) {
        val b = bodies[id] ?: return
        val (w, h) = sizes.getValue(id)
        b.setTransform(Vec2(m(x + w / 2f), m(y + h / 2f)), b.angle)
        b.isAwake = true
        wakeups.trySend(Unit)
    }

    /** Current poses for writing back into the layout once everything is still. */
    fun poses(): Map<String, Triple<Float, Float, Float>> =
        states.mapValues { (_, s) -> Triple(s.x, s.y, s.rotation) }

    companion object {
        const val PPM = 50f
        private const val STEP = 1f / 60f
    }
}
