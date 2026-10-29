<script setup lang="ts">
import { onBeforeUnmount, onMounted, useTemplateRef } from 'vue'

interface BeamConfig {
  family: 'upper' | 'lower'
  offset: number
  amplitude: number
  frequency: number
  phase: number
  speed: number
  hue: number
  alpha: number
  thickness: number
}

interface Point2D {
  x: number
  y: number
}

interface DrawCall {
  offset: number
  count: number
}

interface BeamProgramInfo {
  program: WebGLProgram
  position: number
  color: number
  alpha: number
}

const canvasRef = useTemplateRef<HTMLCanvasElement>('canvas')

const beams: BeamConfig[] = [
  {
    family: 'upper',
    offset: -132,
    amplitude: 42,
    frequency: 1.12,
    phase: 0.1,
    speed: 0.00018,
    hue: 198,
    alpha: 0.5,
    thickness: 2.6,
  },
  {
    family: 'upper',
    offset: -104,
    amplitude: 38,
    frequency: 1.2,
    phase: 0.7,
    speed: 0.0002,
    hue: 184,
    alpha: 0.45,
    thickness: 2.2,
  },
  {
    family: 'upper',
    offset: -78,
    amplitude: 46,
    frequency: 1.32,
    phase: 1.25,
    speed: 0.00016,
    hue: 308,
    alpha: 0.42,
    thickness: 2.4,
  },
  {
    family: 'upper',
    offset: -52,
    amplitude: 34,
    frequency: 1.46,
    phase: 1.8,
    speed: 0.00022,
    hue: 44,
    alpha: 0.34,
    thickness: 2,
  },
  {
    family: 'upper',
    offset: -25,
    amplitude: 44,
    frequency: 1.24,
    phase: 2.4,
    speed: 0.00018,
    hue: 292,
    alpha: 0.46,
    thickness: 2.3,
  },
  {
    family: 'upper',
    offset: 2,
    amplitude: 40,
    frequency: 1.54,
    phase: 3,
    speed: 0.0002,
    hue: 168,
    alpha: 0.48,
    thickness: 2.5,
  },
  {
    family: 'upper',
    offset: 30,
    amplitude: 52,
    frequency: 1.16,
    phase: 3.55,
    speed: 0.00017,
    hue: 320,
    alpha: 0.4,
    thickness: 2.1,
  },
  {
    family: 'upper',
    offset: 58,
    amplitude: 36,
    frequency: 1.4,
    phase: 4.2,
    speed: 0.00021,
    hue: 210,
    alpha: 0.46,
    thickness: 2.3,
  },
  {
    family: 'upper',
    offset: 86,
    amplitude: 42,
    frequency: 1.28,
    phase: 4.85,
    speed: 0.00019,
    hue: 35,
    alpha: 0.32,
    thickness: 2,
  },
  {
    family: 'lower',
    offset: -72,
    amplitude: 56,
    frequency: 1.08,
    phase: 0.35,
    speed: 0.00015,
    hue: 270,
    alpha: 0.2,
    thickness: 2.2,
  },
  {
    family: 'lower',
    offset: -38,
    amplitude: 52,
    frequency: 1.18,
    phase: 1.15,
    speed: 0.00016,
    hue: 292,
    alpha: 0.24,
    thickness: 2.4,
  },
  {
    family: 'lower',
    offset: -8,
    amplitude: 64,
    frequency: 1.02,
    phase: 2.1,
    speed: 0.00014,
    hue: 224,
    alpha: 0.19,
    thickness: 2,
  },
  {
    family: 'lower',
    offset: 28,
    amplitude: 48,
    frequency: 1.3,
    phase: 2.85,
    speed: 0.00017,
    hue: 306,
    alpha: 0.2,
    thickness: 2.1,
  },
  {
    family: 'lower',
    offset: 62,
    amplitude: 58,
    frequency: 1.12,
    phase: 3.75,
    speed: 0.00015,
    hue: 256,
    alpha: 0.18,
    thickness: 2,
  },
]

let gl: WebGLRenderingContext | null = null
let beamProgram: BeamProgramInfo | null = null
let beamBuffer: WebGLBuffer | null = null
let animationFrameId: number | null = null
let motionQuery: MediaQueryList | null = null
let width = 0
let height = 0
let pixelRatio = 1
let startedAt = 0
let pointerX = 0
let pointerY = 0
let smoothPointerX = 0
let smoothPointerY = 0
let forceFieldX = 0
let forceFieldY = 0
let pointerInfluence = 0
let forceInfluence = 0
let targetPointerInfluence = 0

const vertexSize = 6
const tau = Math.PI * 2

function createShader(drawingContext: WebGLRenderingContext, type: number, source: string) {
  const shader = drawingContext.createShader(type)

  if (!shader) {
    return null
  }

  drawingContext.shaderSource(shader, source)
  drawingContext.compileShader(shader)

  if (!drawingContext.getShaderParameter(shader, drawingContext.COMPILE_STATUS)) {
    console.warn(drawingContext.getShaderInfoLog(shader))
    drawingContext.deleteShader(shader)
    return null
  }

  return shader
}

function createProgram(
  drawingContext: WebGLRenderingContext,
  vertexSource: string,
  fragmentSource: string,
) {
  const vertexShader = createShader(drawingContext, drawingContext.VERTEX_SHADER, vertexSource)
  const fragmentShader = createShader(drawingContext, drawingContext.FRAGMENT_SHADER, fragmentSource)

  if (!vertexShader || !fragmentShader) {
    return null
  }

  const program = drawingContext.createProgram()

  if (!program) {
    return null
  }

  drawingContext.attachShader(program, vertexShader)
  drawingContext.attachShader(program, fragmentShader)
  drawingContext.linkProgram(program)
  drawingContext.deleteShader(vertexShader)
  drawingContext.deleteShader(fragmentShader)

  if (!drawingContext.getProgramParameter(program, drawingContext.LINK_STATUS)) {
    console.warn(drawingContext.getProgramInfoLog(program))
    drawingContext.deleteProgram(program)
    return null
  }

  return program
}

function initPrograms(drawingContext: WebGLRenderingContext) {
  const beamVertexSource = `
    attribute vec2 aPosition;
    attribute vec3 aColor;
    attribute float aAlpha;
    varying vec3 vColor;
    varying float vAlpha;

    void main() {
      vColor = aColor;
      vAlpha = aAlpha;
      gl_Position = vec4(aPosition, 0.0, 1.0);
    }
  `
  const beamFragmentSource = `
    precision mediump float;
    varying vec3 vColor;
    varying float vAlpha;

    void main() {
      gl_FragColor = vec4(vColor, vAlpha);
    }
  `
  const nextBeamProgram = createProgram(drawingContext, beamVertexSource, beamFragmentSource)

  if (!nextBeamProgram) {
    return false
  }

  beamProgram = {
    program: nextBeamProgram,
    position: drawingContext.getAttribLocation(nextBeamProgram, 'aPosition'),
    color: drawingContext.getAttribLocation(nextBeamProgram, 'aColor'),
    alpha: drawingContext.getAttribLocation(nextBeamProgram, 'aAlpha'),
  }
  beamBuffer = drawingContext.createBuffer()

  return Boolean(beamBuffer)
}

function resizeCanvas() {
  const canvas = canvasRef.value

  if (!canvas || !gl) {
    return
  }

  pixelRatio = Math.min(window.devicePixelRatio || 1, 1.75)
  width = window.innerWidth
  height = window.innerHeight
  canvas.width = Math.round(width * pixelRatio)
  canvas.height = Math.round(height * pixelRatio)
  canvas.style.width = `${width}px`
  canvas.style.height = `${height}px`
  gl.viewport(0, 0, canvas.width, canvas.height)

  if (smoothPointerX === 0 && smoothPointerY === 0) {
    pointerX = width * 0.72
    pointerY = height * 0.24
    smoothPointerX = pointerX
    smoothPointerY = pointerY
    forceFieldX = pointerX
    forceFieldY = pointerY
  }
}

function updatePointer(event: PointerEvent) {
  pointerX = event.clientX
  pointerY = event.clientY
  targetPointerInfluence = 1
}

function releasePointer() {
  targetPointerInfluence = 0
}

function hslToRgb(hue: number, saturation: number, lightness: number) {
  const chroma = (1 - Math.abs(2 * lightness - 1)) * saturation
  const huePrime = hue / 60
  const secondary = chroma * (1 - Math.abs((huePrime % 2) - 1))
  const match = lightness - chroma / 2
  let red = 0
  let green = 0
  let blue = 0

  if (huePrime >= 0 && huePrime < 1) {
    red = chroma
    green = secondary
  } else if (huePrime >= 1 && huePrime < 2) {
    red = secondary
    green = chroma
  } else if (huePrime >= 2 && huePrime < 3) {
    green = chroma
    blue = secondary
  } else if (huePrime >= 3 && huePrime < 4) {
    green = secondary
    blue = chroma
  } else if (huePrime >= 4 && huePrime < 5) {
    red = secondary
    blue = chroma
  } else {
    red = chroma
    blue = secondary
  }

  return [red + match, green + match, blue + match]
}

function toClipX(x: number) {
  return (x / width) * 2 - 1
}

function toClipY(y: number) {
  return 1 - (y / height) * 2
}

function getBeamPoint(beam: BeamConfig, x: number, elapsed: number, beamIndex: number) {
  const normalizedX = (x + width * 0.12) / (width * 1.24)
  const drift = elapsed * beam.speed
  const softBend = Math.sin((normalizedX * beam.frequency + drift + beam.phase) * tau) * beam.amplitude
  const fineBend =
    Math.sin((normalizedX * (beam.frequency * 2.2) - drift * 0.7 + beam.phase) * tau) *
    beam.amplitude *
    0.22
  const crossing = Math.sin((normalizedX - 0.18) * Math.PI) * (beam.family === 'upper' ? -70 : 130)
  const baseY =
    beam.family === 'upper'
      ? height * (0.02 + normalizedX * 0.34) + beam.offset
      : height * (0.58 - normalizedX * 0.18) + beam.offset + crossing
  let y = baseY + softBend + fineBend
  let adjustedX = x

  if (forceInfluence > 0.01) {
    const dx = adjustedX - forceFieldX
    const dy = y - forceFieldY
    const distance = Math.hypot(dx, dy)
    const radius = Math.min(390, Math.max(220, width * 0.24))

    if (distance < radius) {
      const normalizedDistance = distance / radius
      const falloff = Math.exp(-normalizedDistance * normalizedDistance * 3.2)
      const liftDirection = Math.tanh(dy / 120)
      const sideDirection = Math.tanh(dx / 140)
      const ripple = Math.sin(normalizedDistance * Math.PI + elapsed * 0.0024 + beamIndex * 0.38)
      const softForce = falloff * forceInfluence

      y += liftDirection * softForce * 64
      y += ripple * softForce * 14
      adjustedX += sideDirection * softForce * 18
    }
  }

  const shimmer = Math.sin(elapsed * 0.0012 + normalizedX * 10 + beamIndex) * 0.5 + 0.5

  return {
    x: adjustedX,
    y,
    shimmer,
  }
}

function createBeamPoints(beam: BeamConfig, elapsed: number, beamIndex: number) {
  const points: Array<Point2D & { shimmer: number }> = []
  const segments = Math.max(180, Math.floor(width / 8))
  const startX = -width * 0.12
  const endX = width * 1.12
  const step = (endX - startX) / segments

  for (let index = 0; index <= segments; index += 1) {
    points.push(getBeamPoint(beam, startX + step * index, elapsed, beamIndex))
  }

  return points
}

function appendStrip(
  vertices: number[],
  drawCalls: DrawCall[],
  points: Array<Point2D & { shimmer: number }>,
  thickness: number,
  color: number[],
  alpha: number,
) {
  const start = vertices.length / vertexSize

  for (let index = 0; index < points.length; index += 1) {
    const previous = points[Math.max(0, index - 1)]
    const current = points[index]
    const next = points[Math.min(points.length - 1, index + 1)]
    const tangentX = next.x - previous.x
    const tangentY = next.y - previous.y
    const tangentLength = Math.hypot(tangentX, tangentY) || 1
    const normalX = -tangentY / tangentLength
    const normalY = tangentX / tangentLength
    const localAlpha = alpha * (0.82 + current.shimmer * 0.28)
    const halfThickness = thickness / 2
    const leftX = current.x + normalX * halfThickness
    const leftY = current.y + normalY * halfThickness
    const rightX = current.x - normalX * halfThickness
    const rightY = current.y - normalY * halfThickness

    vertices.push(toClipX(leftX), toClipY(leftY), color[0], color[1], color[2], localAlpha)
    vertices.push(toClipX(rightX), toClipY(rightY), color[0], color[1], color[2], localAlpha)
  }

  drawCalls.push({
    offset: start,
    count: points.length * 2,
  })
}

function buildBeamGeometry(elapsed: number) {
  const vertices: number[] = []
  const drawCalls: DrawCall[] = []

  for (const [beamIndex, beam] of beams.entries()) {
    const points = createBeamPoints(beam, elapsed, beamIndex)
    const hue = (beam.hue + Math.sin(elapsed * 0.00016 + beam.phase) * 18 + 360) % 360
    const coreColor = hslToRgb(hue, 0.82, 0.72)
    const glowColor = hslToRgb((hue + 18) % 360, 0.76, 0.62)
    const haloColor = hslToRgb((hue + 46) % 360, 0.72, 0.78)

    appendStrip(vertices, drawCalls, points, beam.thickness * 16, haloColor, beam.alpha * 0.012)
    appendStrip(vertices, drawCalls, points, beam.thickness * 8, glowColor, beam.alpha * 0.026)
    appendStrip(vertices, drawCalls, points, beam.thickness * 3.4, coreColor, beam.alpha * 0.075)
    appendStrip(vertices, drawCalls, points, beam.thickness, coreColor, beam.alpha * 0.35)
  }

  return {
    data: new Float32Array(vertices),
    drawCalls,
  }
}

function drawBeams(elapsed: number) {
  if (!gl || !beamProgram || !beamBuffer) {
    return
  }

  const { data, drawCalls } = buildBeamGeometry(elapsed)
  const stride = vertexSize * Float32Array.BYTES_PER_ELEMENT

  gl.blendFunc(gl.SRC_ALPHA, gl.ONE)
  gl.useProgram(beamProgram.program)
  gl.bindBuffer(gl.ARRAY_BUFFER, beamBuffer)
  gl.bufferData(gl.ARRAY_BUFFER, data, gl.DYNAMIC_DRAW)
  gl.enableVertexAttribArray(beamProgram.position)
  gl.enableVertexAttribArray(beamProgram.color)
  gl.enableVertexAttribArray(beamProgram.alpha)
  gl.vertexAttribPointer(beamProgram.position, 2, gl.FLOAT, false, stride, 0)
  gl.vertexAttribPointer(
    beamProgram.color,
    3,
    gl.FLOAT,
    false,
    stride,
    2 * Float32Array.BYTES_PER_ELEMENT,
  )
  gl.vertexAttribPointer(
    beamProgram.alpha,
    1,
    gl.FLOAT,
    false,
    stride,
    5 * Float32Array.BYTES_PER_ELEMENT,
  )

  for (const drawCall of drawCalls) {
    gl.drawArrays(gl.TRIANGLE_STRIP, drawCall.offset, drawCall.count)
  }
}

function drawScene(now: number) {
  if (!gl) {
    return
  }

  const elapsed = now - startedAt
  smoothPointerX += (pointerX - smoothPointerX) * 0.12
  smoothPointerY += (pointerY - smoothPointerY) * 0.12
  forceFieldX += (smoothPointerX - forceFieldX) * 0.045
  forceFieldY += (smoothPointerY - forceFieldY) * 0.045
  pointerInfluence += (targetPointerInfluence - pointerInfluence) * 0.08
  forceInfluence += (pointerInfluence - forceInfluence) * 0.052

  gl.clear(gl.COLOR_BUFFER_BIT)
  drawBeams(elapsed)
}

function loop(now: number) {
  drawScene(now)
  animationFrameId = window.requestAnimationFrame(loop)
}

function stopLoop() {
  if (animationFrameId === null) {
    return
  }

  window.cancelAnimationFrame(animationFrameId)
  animationFrameId = null
}

function startLoop() {
  stopLoop()

  if (motionQuery?.matches) {
    targetPointerInfluence = 0
    pointerInfluence = 0
    forceInfluence = 0
    drawScene(performance.now())
    return
  }

  animationFrameId = window.requestAnimationFrame(loop)
}

function handleResize() {
  resizeCanvas()
  drawScene(performance.now())
}

function initializeWebgl() {
  const canvas = canvasRef.value

  if (!canvas) {
    return false
  }

  const drawingContext = canvas.getContext('webgl', {
    alpha: true,
    antialias: true,
    premultipliedAlpha: true,
    powerPreference: 'high-performance',
  })

  if (!drawingContext || !initPrograms(drawingContext)) {
    return false
  }

  gl = drawingContext
  gl.enable(gl.BLEND)
  gl.disable(gl.DEPTH_TEST)
  gl.clearColor(0, 0, 0, 0)

  return true
}

onMounted(() => {
  startedAt = performance.now()
  motionQuery = window.matchMedia('(prefers-reduced-motion: reduce)')

  if (!initializeWebgl()) {
    return
  }

  resizeCanvas()
  startLoop()
  window.addEventListener('resize', handleResize, { passive: true })
  window.addEventListener('pointermove', updatePointer, { passive: true })
  window.addEventListener('pointerleave', releasePointer, { passive: true })
  document.addEventListener('mouseleave', releasePointer, { passive: true })
  motionQuery.addEventListener('change', startLoop)
})

onBeforeUnmount(() => {
  stopLoop()
  window.removeEventListener('resize', handleResize)
  window.removeEventListener('pointermove', updatePointer)
  window.removeEventListener('pointerleave', releasePointer)
  document.removeEventListener('mouseleave', releasePointer)
  motionQuery?.removeEventListener('change', startLoop)

  if (gl) {
    if (beamProgram) {
      gl.deleteProgram(beamProgram.program)
    }

    if (beamBuffer) {
      gl.deleteBuffer(beamBuffer)
    }
  }
})
</script>

<template>
  <div class="ribbon-background" aria-hidden="true">
    <canvas ref="canvas" class="ribbon-canvas" data-engine="native-webgl" />
  </div>
</template>

<style scoped>
.ribbon-background {
  position: fixed;
  inset: 0;
  z-index: 0;
  overflow: hidden;
  pointer-events: none;
  background:
    radial-gradient(circle at 20% 20%, rgba(194, 224, 210, 0.46), transparent 30rem),
    radial-gradient(circle at 78% 18%, rgba(168, 195, 206, 0.42), transparent 32rem),
    radial-gradient(circle at 55% 86%, rgba(250, 250, 250, 0.48), transparent 34rem),
    linear-gradient(145deg, #f8faf9 0%, #ffffff 42%, #cccccc 100%);
}

.ribbon-canvas {
  display: block;
  width: 100%;
  height: 100%;
}

@media (prefers-color-scheme: dark) {
  .ribbon-background {
    background:
      radial-gradient(circle at 46% 0%, rgba(170, 220, 255, 0.2), transparent 34rem),
      radial-gradient(circle at 24% 100%, rgba(248, 248, 248, 0.2), transparent 34rem),
      linear-gradient(
        145deg,
        rgba(8, 18, 47, 0.58),
        rgba(61, 17, 78, 0.4) 54%,
        rgba(2, 8, 24, 0.5)
      );
  }
}
</style>
