import { useEffect, useMemo, useRef, type ComponentRef } from 'react';
import { Canvas, useFrame } from '@react-three/fiber';
import { Edges, OrbitControls } from '@react-three/drei';
import { Vector3 } from 'three';
import type { components } from '../../api/schema';
import {
  PLATE,
  ROOM_HEIGHT,
  SCENE_BACKDROP,
  readScenePalette,
  readSeatPalette,
} from '../../lib/floor3d';
import type { SeatTileModel } from '../../lib/seatModel';
import { Room3D } from './Room3D';
import { Pod3D } from './Pod3D';
import { useFloorScene } from './useFloorScene';

type SeatMapResponse = components['schemas']['SeatMapResponse'];
type ControlsRef = ComponentRef<typeof OrbitControls>;

interface Floor3DSceneProps {
  seatMap: SeatMapResponse;
  currentUserId: number | null;
  pendingSeatId: number | null;
  selectedSeatId: number | null;
  reducedMotion: boolean;
  onSelectSeat: (seat: SeatTileModel) => void;
  onHoverSeat: (seat: SeatTileModel | null) => void;
}

/** Where the camera parks when nothing is picked: the whole floor, from above. */
const OVERVIEW_TARGET = new Vector3(0, 0.5, 0);
const OVERVIEW_POSITION = new Vector3(0, 44, 48);

export function Floor3DScene({
  seatMap,
  currentUserId,
  pendingSeatId,
  selectedSeatId,
  reducedMotion,
  onSelectSeat,
  onHoverSeat,
}: Floor3DSceneProps) {
  const scene = useFloorScene(seatMap, currentUserId, pendingSeatId);
  const palette = useMemo(() => readScenePalette(), []);
  const seatColors = useMemo(() => readSeatPalette(), []);
  const controlsRef = useRef<ControlsRef>(null);

  const focus = selectedSeatId != null ? scene.seatsById.get(selectedSeatId) : undefined;
  const focusKey = focus ? `${focus.position[0]}:${focus.position[2]}` : 'overview';

  return (
    <Canvas
      shadows
      // Capped below full retina density: the model is flat-shaded blocks, so
      // the extra pixels buy almost nothing and cost real time on first paint.
      dpr={[1, 1.5]}
      camera={{ position: OVERVIEW_POSITION.toArray(), fov: 42, near: 0.1, far: 400 }}
      onPointerMissed={() => onHoverSeat(null)}
    >
      <color attach="background" args={[SCENE_BACKDROP]} />
      <fog attach="fog" args={[SCENE_BACKDROP, 90, 210]} />

      <ambientLight intensity={1.1} />
      <hemisphereLight args={[palette.paper, SCENE_BACKDROP, 0.7]} />
      <directionalLight
        position={[38, 52, 26]}
        intensity={1.5}
        castShadow
        shadow-mapSize={[1024, 1024]}
        shadow-camera-left={-70}
        shadow-camera-right={70}
        shadow-camera-top={45}
        shadow-camera-bottom={-45}
        shadow-camera-far={160}
      />

      {/* The building slab — everything above sits on this. */}
      <mesh position={[0, -0.3, 0]} receiveShadow>
        <boxGeometry args={[PLATE.w + 3, 0.6, PLATE.d + 3]} />
        <meshStandardMaterial color={palette.paperDim} roughness={0.95} />
        <Edges threshold={15} color={palette.ink} />
      </mesh>

      {scene.rooms.map((volume) => (
        <Room3D
          key={volume.room.id}
          volume={volume}
          inkColor={palette.ink}
          fill={palette.room[volume.room.category]}
          // Flat pads carry their name on the floor plan already; labelling them
          // here would stack text on top of every desk in the workspace.
          showLabel={ROOM_HEIGHT[volume.room.category] > 0.3}
        />
      ))}

      {scene.pods.map((pod) => (
        <Pod3D
          key={pod.tableId}
          pod={pod}
          seatColors={seatColors}
          inkColor={palette.ink}
          deskColor={palette.paper}
          selectedSeatId={selectedSeatId}
          reducedMotion={reducedMotion}
          onSelect={onSelectSeat}
          onHover={onHoverSeat}
        />
      ))}

      <OrbitControls
        ref={controlsRef}
        makeDefault
        enablePan
        minDistance={6}
        maxDistance={120}
        // Stop the orbit at the horizon so the camera never dives under the slab.
        maxPolarAngle={Math.PI / 2 - 0.06}
        dampingFactor={0.08}
      />

      <CameraRig
        key={focusKey}
        controlsRef={controlsRef}
        focus={focus ? new Vector3(...focus.position) : null}
        reducedMotion={reducedMotion}
      />
    </Canvas>
  );
}

interface CameraRigProps {
  controlsRef: React.RefObject<ControlsRef | null>;
  focus: Vector3 | null;
  reducedMotion: boolean;
}

/**
 * Flies the camera to the picked seat, then gets out of the way. The flight is
 * the point — it answers "where in the office is R4-B2?" in one continuous move
 * instead of making you find it. Any drag cancels it immediately so the user
 * always wins control back, and reduced-motion cuts straight to the destination.
 */
function CameraRig({ controlsRef, focus, reducedMotion }: CameraRigProps) {
  const flying = useRef(true);

  const desired = useMemo(() => {
    if (!focus) return { position: OVERVIEW_POSITION.clone(), target: OVERVIEW_TARGET.clone() };
    return {
      position: new Vector3(focus.x + 9, 12.5, focus.z + 16),
      target: new Vector3(focus.x, 1.2, focus.z),
    };
  }, [focus]);

  useEffect(() => {
    const controls = controlsRef.current;
    if (!controls) return;
    const onStart = () => {
      flying.current = false;
    };
    controls.addEventListener('start', onStart);
    return () => controls.removeEventListener('start', onStart);
  }, [controlsRef]);

  useFrame((state, delta) => {
    const controls = controlsRef.current;
    if (!controls || !flying.current) return;

    if (reducedMotion) {
      state.camera.position.copy(desired.position);
      controls.target.copy(desired.target);
      controls.update();
      flying.current = false;
      return;
    }

    const k = 1 - Math.pow(0.0016, Math.min(delta, 0.05));
    state.camera.position.lerp(desired.position, k);
    controls.target.lerp(desired.target, k);
    controls.update();

    if (state.camera.position.distanceTo(desired.position) < 0.12) flying.current = false;
  });

  return null;
}
