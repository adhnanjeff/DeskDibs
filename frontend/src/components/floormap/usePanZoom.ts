import { useCallback, useEffect, useLayoutEffect, useRef, useState } from 'react';
import type { PointerEvent as ReactPointerEvent } from 'react';
import { CANVAS } from '../../lib/floorPlan';

interface Transform {
  scale: number;
  x: number;
  y: number;
}

const MAX_SCALE = 3;

/**
 * Pan and zoom for the floor canvas: drag to pan, wheel or pinch to zoom around
 * the pointer, and buttons to step. The canvas fits the viewport width at rest
 * (that fit scale is the minimum), so the whole office is visible before the
 * user explores. Everything scales uniformly — text, borders and tiles alike.
 */
export function usePanZoom() {
  const viewportRef = useRef<HTMLDivElement | null>(null);
  const [transform, setTransform] = useState<Transform>({ scale: 1, x: 0, y: 0 });
  const [minScale, setMinScale] = useState(1);

  const pointers = useRef<Map<number, { x: number; y: number }>>(new Map());
  const panStart = useRef<{ x: number; y: number; tx: number; ty: number } | null>(null);
  const pinchStart = useRef<{
    dist: number;
    scale: number;
    midX: number;
    midY: number;
    x: number;
    y: number;
  } | null>(null);

  const clampScale = useCallback(
    (s: number) => Math.min(MAX_SCALE, Math.max(minScale, s)),
    [minScale],
  );

  const fit = useCallback(() => {
    const vp = viewportRef.current;
    if (!vp || vp.clientWidth === 0) return;
    const scale = vp.clientWidth / CANVAS.w;
    setMinScale(scale);
    setTransform({ scale, x: 0, y: 0 });
  }, []);

  useLayoutEffect(() => {
    const vp = viewportRef.current;
    if (!vp) return;
    fit();
    // Re-fit once layout settles: the first measure can catch a momentarily
    // full-width column before the two-column grid applies.
    const raf = requestAnimationFrame(fit);
    const ro = new ResizeObserver(() => fit());
    ro.observe(vp);
    window.addEventListener('resize', fit);
    return () => {
      cancelAnimationFrame(raf);
      ro.disconnect();
      window.removeEventListener('resize', fit);
    };
  }, [fit]);

  useEffect(() => {
    const vp = viewportRef.current;
    if (!vp) return;
    const onWheel = (e: WheelEvent) => {
      e.preventDefault();
      const rect = vp.getBoundingClientRect();
      const px = e.clientX - rect.left;
      const py = e.clientY - rect.top;
      setTransform((t) => {
        const next = Math.min(MAX_SCALE, Math.max(minScale, t.scale * Math.exp(-e.deltaY * 0.0015)));
        const k = next / t.scale;
        return { scale: next, x: px - (px - t.x) * k, y: py - (py - t.y) * k };
      });
    };
    vp.addEventListener('wheel', onWheel, { passive: false });
    return () => vp.removeEventListener('wheel', onWheel);
  }, [minScale]);

  const onPointerDown = (e: ReactPointerEvent) => {
    const vp = viewportRef.current;
    if (!vp) return;
    (e.target as Element).setPointerCapture?.(e.pointerId);
    pointers.current.set(e.pointerId, { x: e.clientX, y: e.clientY });
    if (pointers.current.size === 2) {
      const [a, b] = [...pointers.current.values()];
      const rect = vp.getBoundingClientRect();
      pinchStart.current = {
        dist: Math.hypot(a.x - b.x, a.y - b.y),
        scale: transform.scale,
        midX: (a.x + b.x) / 2 - rect.left,
        midY: (a.y + b.y) / 2 - rect.top,
        x: transform.x,
        y: transform.y,
      };
      panStart.current = null;
    } else {
      panStart.current = { x: e.clientX, y: e.clientY, tx: transform.x, ty: transform.y };
    }
  };

  const onPointerMove = (e: ReactPointerEvent) => {
    if (!pointers.current.has(e.pointerId)) return;
    pointers.current.set(e.pointerId, { x: e.clientX, y: e.clientY });

    if (pointers.current.size >= 2 && pinchStart.current) {
      const [a, b] = [...pointers.current.values()];
      const dist = Math.hypot(a.x - b.x, a.y - b.y);
      const ps = pinchStart.current;
      const next = clampScale(ps.scale * (dist / ps.dist));
      const k = next / ps.scale;
      setTransform({ scale: next, x: ps.midX - (ps.midX - ps.x) * k, y: ps.midY - (ps.midY - ps.y) * k });
    } else if (panStart.current) {
      const dx = e.clientX - panStart.current.x;
      const dy = e.clientY - panStart.current.y;
      if (Math.abs(dx) > 3 || Math.abs(dy) > 3) {
        const start = panStart.current;
        setTransform((t) => ({ ...t, x: start.tx + dx, y: start.ty + dy }));
      }
    }
  };

  const onPointerUp = (e: ReactPointerEvent) => {
    pointers.current.delete(e.pointerId);
    if (pointers.current.size < 2) pinchStart.current = null;
    if (pointers.current.size === 0) {
      panStart.current = null;
    } else {
      const [pt] = [...pointers.current.values()];
      panStart.current = { x: pt.x, y: pt.y, tx: transform.x, ty: transform.y };
    }
  };

  const zoomBy = (factor: number) => {
    const vp = viewportRef.current;
    if (!vp) return;
    const cx = vp.clientWidth / 2;
    const cy = vp.clientHeight / 2;
    setTransform((t) => {
      const next = clampScale(t.scale * factor);
      const k = next / t.scale;
      return { scale: next, x: cx - (cx - t.x) * k, y: cy - (cy - t.y) * k };
    });
  };

  return {
    viewportRef,
    transform,
    canZoomOut: transform.scale > minScale + 0.001,
    canZoomIn: transform.scale < MAX_SCALE - 0.001,
    onPointerDown,
    onPointerMove,
    onPointerUp,
    zoomIn: () => zoomBy(1.25),
    zoomOut: () => zoomBy(0.8),
    reset: fit,
  };
}
