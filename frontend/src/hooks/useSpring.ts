import { useEffect, useRef, useState } from 'react'

interface SpringConfig {
  stiffness: number
  damping: number
}

export function useSpring(
  target: number,
  config: SpringConfig = { stiffness: 100, damping: 20 },
): number {
  const [value, setValue] = useState(0)
  const posRef = useRef(0)
  const velRef = useRef(0)
  const frameRef = useRef<number>()
  const prevTimeRef = useRef<number>()

  useEffect(() => {
    const { stiffness, damping } = config
    prevTimeRef.current = undefined

    const animate = (time: number) => {
      if (prevTimeRef.current === undefined) {
        prevTimeRef.current = time
      }
      // Cap dt at 50ms to prevent instability after tab switch
      const dt = Math.min((time - prevTimeRef.current) / 1000, 0.05)
      prevTimeRef.current = time

      const accel = stiffness * (target - posRef.current) - damping * velRef.current
      velRef.current = velRef.current + accel * dt
      posRef.current = posRef.current + velRef.current * dt

      const settled =
        Math.abs(posRef.current - target) < 0.001 && Math.abs(velRef.current) < 0.005

      if (settled) {
        posRef.current = target
        velRef.current = 0
        setValue(target)
      } else {
        setValue(posRef.current)
        frameRef.current = requestAnimationFrame(animate)
      }
    }

    frameRef.current = requestAnimationFrame(animate)
    return () => {
      if (frameRef.current) cancelAnimationFrame(frameRef.current)
    }
  }, [target, config.stiffness, config.damping])

  return value
}
