import os
import sys
import numpy as np
import trimesh

def create_3d_hat(out_path):
    print(f"Creating 3D Hat mesh at: {out_path}")
    os.makedirs(os.path.dirname(os.path.abspath(out_path)), exist_ok=True)
    
    # 1. Brim (Disk)
    brim = trimesh.creation.cylinder(radius=2.2, height=0.08, sections=64)
    brim.apply_translation([0, 0, 0.04])

    # 2. Crown (Main Hat Body)
    crown = trimesh.creation.cylinder(radius=1.2, height=1.1, sections=64)
    crown.apply_translation([0, 0, 0.6])

    # 3. Hat Band (Accent Ring)
    band = trimesh.creation.cylinder(radius=1.24, height=0.18, sections=64)
    band.apply_translation([0, 0, 0.2])

    # Combine & center
    hat = trimesh.util.concatenate([brim, crown, band])
    trimesh.repair.fix_normals(hat)
    trimesh.repair.fill_holes(hat)
    
    # Export OBJ
    hat.export(out_path)
    print(f"✅ Created 3D Hat: {out_path} ({len(hat.vertices)} vertices, {len(hat.faces)} faces)")
    return out_path

def create_3d_shark(out_path):
    print(f"Creating 3D Shark mesh at: {out_path}")
    os.makedirs(os.path.dirname(os.path.abspath(out_path)), exist_ok=True)

    # 1. Shark Body (Streamlined Torpedo/Ellipsoid)
    body = trimesh.creation.icosphere(subdivisions=4, radius=1.0)
    # Stretch X for length, compress Y for sleek body, Z for height
    matrix_body = np.diag([3.2, 0.7, 0.9, 1.0])
    body.apply_transform(matrix_body)

    # 2. Dorsal Fin (Top Fin)
    dorsal = trimesh.creation.cone(radius=0.5, height=0.9, sections=32)
    # Rotate and slant backwards
    dorsal.apply_transform(trimesh.transformations.rotation_matrix(np.radians(-25), [0, 1, 0]))
    dorsal.apply_translation([0.2, 0, 0.85])

    # 3. Pectoral Fins (Left & Right)
    fin_left = trimesh.creation.cone(radius=0.4, height=1.1, sections=32)
    fin_left.apply_transform(trimesh.transformations.rotation_matrix(np.radians(65), [1, 0, 0]))
    fin_left.apply_transform(trimesh.transformations.rotation_matrix(np.radians(20), [0, 0, 1]))
    fin_left.apply_translation([0.5, 0.8, -0.2])

    fin_right = trimesh.creation.cone(radius=0.4, height=1.1, sections=32)
    fin_right.apply_transform(trimesh.transformations.rotation_matrix(np.radians(-65), [1, 0, 0]))
    fin_right.apply_transform(trimesh.transformations.rotation_matrix(np.radians(-20), [0, 0, 1]))
    fin_right.apply_translation([0.5, -0.8, -0.2])

    # 4. Caudal Tail Fin
    tail_upper = trimesh.creation.cone(radius=0.3, height=0.9, sections=32)
    tail_upper.apply_transform(trimesh.transformations.rotation_matrix(np.radians(45), [0, 1, 0]))
    tail_upper.apply_translation([-3.0, 0, 0.4])

    tail_lower = trimesh.creation.cone(radius=0.25, height=0.6, sections=32)
    tail_lower.apply_transform(trimesh.transformations.rotation_matrix(np.radians(-45), [0, 1, 0]))
    tail_lower.apply_translation([-3.0, 0, -0.3])

    # Combine & repair
    shark = trimesh.util.concatenate([body, dorsal, fin_left, fin_right, tail_upper, tail_lower])
    trimesh.repair.fix_normals(shark)
    trimesh.repair.fill_holes(shark)

    # Export OBJ
    shark.export(out_path)
    print(f"✅ Created 3D Shark: {out_path} ({len(shark.vertices)} vertices, {len(shark.faces)} faces)")
    return out_path

if __name__ == '__main__':
    hat_path = os.path.join('backend', 'media', 'users', '1', 'generated_models', 'stylish_hat.obj')
    shark_path = os.path.join('backend', 'media', 'users', '1', 'generated_models', 'ocean_shark.obj')
    
    create_3d_hat(hat_path)
    create_3d_shark(shark_path)
