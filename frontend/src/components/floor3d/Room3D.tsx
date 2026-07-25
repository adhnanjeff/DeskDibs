import { Edges, Html } from '@react-three/drei';
import type { RoomVolume } from '../../lib/floor3d';

interface Room3DProps {
  volume: RoomVolume;
  inkColor: string;
  fill: string;
  showLabel: boolean;
}

/**
 * One room as a massing block: a flat-coloured volume with hard black edges —
 * the Bauhaus outline of the 2D plan, extruded. Flat pads (the open workspace,
 * balcony and collab zones) render as floor insets rather than walls.
 */
export function Room3D({ volume, inkColor, fill, showLabel }: Room3DProps) {
  const { room, position, size, isPad } = volume;

  return (
    <group>
      <mesh position={position} castShadow={!isPad} receiveShadow>
        <boxGeometry args={size} />
        <meshStandardMaterial color={fill} roughness={0.85} metalness={0} />
        <Edges threshold={15} color={inkColor} />
      </mesh>

      {showLabel && (
        <Html
          position={[position[0], position[1] + size[1] / 2 + 0.35, position[2]]}
          center
          // Deliberately NOT distance-scaled: a scaled label balloons to fill
          // the screen up close and vanishes across the floor. Fixed size makes
          // these read like pins on a map at every orbit angle.
          zIndexRange={[10, 0]}
          style={{ pointerEvents: 'none', userSelect: 'none' }}
        >
          <span className="whitespace-nowrap border-2 border-ink bg-paper px-1.5 py-0.5 font-mono text-[10px] font-bold uppercase tracking-wider text-ink">
            {room.label}
          </span>
        </Html>
      )}
    </group>
  );
}
