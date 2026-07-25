import { useRef } from 'react';
import { useFrame } from '@react-three/fiber';
import { Edges, Html } from '@react-three/drei';
import type { Mesh } from 'three';
import { CHAIR } from '../../lib/floor3d';
import type { SeatTileModel } from '../../lib/seatModel';
import type { Pod3D as Pod3DModel, Seat3D } from './useFloorScene';

interface Pod3DProps {
  pod: Pod3DModel;
  seatColors: Record<string, string>;
  inkColor: string;
  deskColor: string;
  selectedSeatId: number | null;
  reducedMotion: boolean;
  onSelect: (seat: SeatTileModel) => void;
  onHover: (seat: SeatTileModel | null) => void;
}

/** One desk pod: the shared work surface plus a chair per seat. */
export function Pod3D({
  pod,
  seatColors,
  inkColor,
  deskColor,
  selectedSeatId,
  reducedMotion,
  onSelect,
  onHover,
}: Pod3DProps) {
  return (
    <group>
      <mesh position={pod.desk.position} castShadow receiveShadow>
        <boxGeometry args={pod.desk.size} />
        <meshStandardMaterial color={deskColor} roughness={0.7} />
        <Edges threshold={15} color={inkColor} />
      </mesh>

      <Html
        position={pod.labelPosition}
        center
        zIndexRange={[9, 0]}
        style={{ pointerEvents: 'none', userSelect: 'none' }}
      >
        <span className="whitespace-nowrap font-mono text-[10px] font-bold uppercase tracking-wider text-ink">
          {pod.label}
        </span>
      </Html>

      {pod.seats.map((s) => (
        <Chair3D
          key={s.seat.seatId}
          placement={s}
          // Selection is pure UI state, exactly as in the 2D tile: it overrides
          // the seat's own colour so the picked desk is unmistakable.
          color={
            s.seat.seatId === selectedSeatId
              ? seatColors.SELECTED
              : (seatColors[s.seat.displayState] ?? seatColors.AVAILABLE)
          }
          inkColor={inkColor}
          selected={s.seat.seatId === selectedSeatId}
          reducedMotion={reducedMotion}
          onSelect={onSelect}
          onHover={onHover}
        />
      ))}
    </group>
  );
}

interface Chair3DProps {
  placement: Seat3D;
  color: string;
  inkColor: string;
  selected: boolean;
  reducedMotion: boolean;
  onSelect: (seat: SeatTileModel) => void;
  onHover: (seat: SeatTileModel | null) => void;
}

/**
 * A single workstation chair. The selected seat carries a beacon above it — the
 * one animated element in the scene, and it only exists to answer "which seat am
 * I looking at?", never as decoration.
 */
function Chair3D({
  placement,
  color,
  inkColor,
  selected,
  reducedMotion,
  onSelect,
  onHover,
}: Chair3DProps) {
  const { seat, position, facing } = placement;
  const beaconRef = useRef<Mesh>(null);

  // The backrest sits on the side away from the desk, so the chair reads as
  // facing its work surface from any orbit angle.
  const backX = -facing * (CHAIR.padW / 2 - CHAIR.backT / 2);

  useFrame((state) => {
    if (!beaconRef.current) return;
    beaconRef.current.position.y = reducedMotion
      ? 2.3
      : 2.3 + Math.sin(state.clock.elapsedTime * 2.6) * 0.16;
  });

  return (
    <group
      position={position}
      onClick={(e) => {
        e.stopPropagation();
        onSelect(seat);
      }}
      onPointerOver={(e) => {
        e.stopPropagation();
        onHover(seat);
      }}
      onPointerOut={() => onHover(null)}
    >
      <mesh position={[0, CHAIR.seatY / 2, 0]} castShadow>
        <cylinderGeometry args={[CHAIR.legR, CHAIR.legR * 1.6, CHAIR.seatY, 10]} />
        <meshStandardMaterial color={inkColor} roughness={0.6} />
      </mesh>

      <mesh position={[0, CHAIR.seatY, 0]} castShadow receiveShadow>
        <boxGeometry args={[CHAIR.padW, CHAIR.padH, CHAIR.padD]} />
        <meshStandardMaterial color={color} roughness={0.75} />
        <Edges threshold={15} color={inkColor} />
      </mesh>

      <mesh position={[backX, CHAIR.seatY + CHAIR.backH / 2, 0]} castShadow>
        <boxGeometry args={[CHAIR.backT, CHAIR.backH, CHAIR.padD]} />
        <meshStandardMaterial color={color} roughness={0.75} />
        <Edges threshold={15} color={inkColor} />
      </mesh>

      {selected && (
        <>
          <mesh position={[0, 0.04, 0]} rotation={[-Math.PI / 2, 0, 0]}>
            <ringGeometry args={[0.85, 1.15, 32]} />
            <meshBasicMaterial color={color} toneMapped={false} />
          </mesh>
          <mesh ref={beaconRef} position={[0, 2.3, 0]} rotation={[Math.PI, 0, 0]}>
            <coneGeometry args={[0.34, 0.8, 4]} />
            <meshStandardMaterial color={color} emissive={color} emissiveIntensity={0.5} />
            <Edges threshold={15} color={inkColor} />
          </mesh>
        </>
      )}
    </group>
  );
}
