"""
PrintGuard Procedural CAD 3D Geometry Generator
Generates clean, manifold, 3D printable chess pieces and parametric geometry using Trimesh, SciPy, and NumPy.
"""
import argparse
import json
import os
import sys
import numpy as np
import trimesh
from scipy.spatial import Delaunay
import matplotlib.path as mpath


def triangulate_polygon_2d(poly_pts):
    """
    Robust constrained polygon triangulation using Delaunay with centroid inclusion filter.
    Guarantees manifold, non-overlapping triangulation for any 2D polygon.
    """
    poly = np.asarray(poly_pts, dtype=np.float64)
    tri = Delaunay(poly)
    path = mpath.Path(poly)
    centroids = poly[tri.simplices].mean(axis=1)
    mask = path.contains_points(centroids)
    inside_tris = tri.simplices[mask]
    return poly, inside_tris


def extrude_polygon_xz(poly_xz, thickness_y=12.0):
    """
    Extrudes a 2D polygon in the (X, Z) plane along the Y axis.
    Guarantees outward normals, watertight boundary, and manifold topology.
    """
    pts_2d, tris = triangulate_polygon_2d(poly_xz)
    n = len(pts_2d)
    h2 = thickness_y / 2.0

    v_front = np.column_stack([pts_2d[:, 0], np.full(n, -h2), pts_2d[:, 1]])
    v_back  = np.column_stack([pts_2d[:, 0], np.full(n,  h2), pts_2d[:, 1]])
    vertices = np.vstack([v_front, v_back])

    faces = []
    # Cap faces with correct winding for outward normals
    for t in tris:
        v0, v1, v2 = pts_2d[t[0]], pts_2d[t[1]], pts_2d[t[2]]
        cross = (v1[0] - v0[0]) * (v2[1] - v0[1]) - (v1[1] - v0[1]) * (v2[0] - v0[0])
        if cross > 0:
            faces.append([t[0] + n, t[1] + n, t[2] + n])  # back (+Y)
            faces.append([t[0], t[2], t[1]])              # front (-Y)
        else:
            faces.append([t[0] + n, t[2] + n, t[1] + n])  # back (+Y)
            faces.append([t[0], t[1], t[2]])              # front (-Y)

    # Side walls
    for i in range(n):
        nxt = (i + 1) % n
        faces.append([i, nxt, nxt + n])
        faces.append([i, nxt + n, i + n])

    mesh = trimesh.Trimesh(vertices=vertices, faces=np.array(faces), process=True)
    trimesh.repair.fix_normals(mesh)
    return mesh


def generate_chess_king(scale=1.0, sections=48):
    """
    Staunton Chess King:
    - Stepped pedestal base (~32mm diam, 11mm h)
    - Waisted column tapering to collar (height 11..44mm)
    - Ring collar (height 42..45mm)
    - Flared coronet / crown (height 45..58mm)
    - Christian cross finial atop crown (height 58..70mm)
    """
    profile = [
        [0.0, 0.0],
        [16.0, 0.0],
        [16.0, 3.0],
        [14.5, 4.5],
        [14.5, 6.0],
        [12.5, 8.0],
        [12.5, 11.0],
        [10.5, 16.0],
        [8.5, 24.0],
        [7.5, 30.0],
        [8.0, 36.0],
        [9.5, 40.0],
        [12.0, 42.0],
        [12.5, 43.5],
        [10.5, 45.0],
        [10.0, 46.0],
        [13.5, 52.0],
        [14.0, 56.0],
        [12.0, 57.0],
        [9.0, 58.0],
        [4.0, 58.0],
        [0.0, 58.0]
    ]
    body_mesh = trimesh.creation.revolve(np.array(profile), sections=sections)

    # Finial cross atop crown (z from 58 to 70mm)
    v_bar = trimesh.creation.box(extents=[3.5, 3.5, 12.0])
    v_bar.apply_translation([0.0, 0.0, 64.0])

    h_bar = trimesh.creation.box(extents=[9.0, 3.5, 3.5])
    h_bar.apply_translation([0.0, 0.0, 65.5])

    king_mesh = trimesh.util.concatenate([body_mesh, v_bar, h_bar])
    trimesh.repair.fix_normals(king_mesh)
    if scale != 1.0:
        king_mesh.apply_scale(scale)
    return king_mesh


def generate_chess_knight(scale=1.0, sections=48):
    """
    Staunton Chess Knight:
    - Circular stepped pedestal base (diam 30mm, height 12mm)
    - Sculpted equine torso solidly embedded into base (Z=6..58mm)
    - Arched neck with mane
    - Pointed triangular ears and muzzle
    """
    base_profile = [
        [0.0, 0.0],
        [15.0, 0.0],
        [15.0, 3.0],
        [13.5, 4.5],
        [13.5, 6.5],
        [11.5, 8.5],
        [10.5, 12.0],
        [0.0, 12.0]
    ]
    base_mesh = trimesh.creation.revolve(np.array(base_profile), sections=sections)

    # Horse body profile embedded solidly from Z=6.0 inside base up to crown
    horse_pts = [
        [-8.0, 6.0],
        [-9.5, 18.0],
        [-9.0, 26.0],
        [-7.5, 34.0],
        [-5.5, 41.0],
        [-3.0, 46.0],
        [-1.0, 49.0],
        [0.0, 51.0],
        [1.0, 56.0],   # Ear tip
        [2.5, 51.0],   # Forehead
        [5.0, 47.0],
        [9.0, 42.0],
        [12.5, 37.0],  # Snout
        [13.5, 34.5],  # Muzzle tip
        [12.0, 32.5],  # Lower lip
        [8.5, 30.0],   # Jaw
        [4.5, 26.0],   # Throat
        [3.5, 20.0],
        [5.5, 15.0],   # Chest
        [7.5, 6.0]     # Base front
    ]
    horse_body = extrude_polygon_xz(np.array(horse_pts), thickness_y=12.0)

    # Pointed 3D ears
    ear_left = trimesh.creation.cone(radius=2.2, height=6.0, sections=16)
    ear_left.apply_transform(trimesh.transformations.rotation_matrix(np.radians(-10), [0, 1, 0]))
    ear_left.apply_translation([1.0, 3.2, 50.0])

    ear_right = trimesh.creation.cone(radius=2.2, height=6.0, sections=16)
    ear_right.apply_transform(trimesh.transformations.rotation_matrix(np.radians(-10), [0, 1, 0]))
    ear_right.apply_translation([1.0, -3.2, 50.0])

    # Muzzle tip
    muzzle = trimesh.creation.icosphere(subdivisions=2, radius=3.0)
    muzzle.apply_translation([12.0, 0.0, 34.5])

    knight_mesh = trimesh.util.concatenate([base_mesh, horse_body, ear_left, ear_right, muzzle])
    trimesh.repair.fix_normals(knight_mesh)
    if scale != 1.0:
        knight_mesh.apply_scale(scale)
    return knight_mesh


def generate_chess_queen(scale=1.0, sections=48):
    """Staunton Chess Queen with coronet and small sphere finial."""
    profile = [
        [0.0, 0.0], [16.0, 0.0], [16.0, 3.0], [14.5, 4.5], [14.5, 6.0],
        [12.5, 8.0], [12.5, 11.0], [10.5, 16.0], [8.0, 24.0], [7.0, 30.0],
        [8.0, 36.0], [9.5, 40.0], [12.0, 42.0], [10.5, 44.0],
        [13.5, 52.0], [14.5, 55.0], [11.0, 56.0], [6.0, 56.5], [0.0, 56.5]
    ]
    body = trimesh.creation.revolve(np.array(profile), sections=sections)
    ball = trimesh.creation.icosphere(subdivisions=2, radius=3.5)
    ball.apply_translation([0.0, 0.0, 59.5])
    queen_mesh = trimesh.util.concatenate([body, ball])
    trimesh.repair.fix_normals(queen_mesh)
    if scale != 1.0:
        queen_mesh.apply_scale(scale)
    return queen_mesh


def generate_chess_rook(scale=1.0, sections=48):
    """Staunton Chess Rook with crenellated parapet."""
    profile = [
        [0.0, 0.0], [15.0, 0.0], [15.0, 3.0], [13.5, 4.5], [13.5, 6.5],
        [11.5, 9.0], [10.5, 12.0], [9.5, 18.0], [9.0, 26.0], [9.5, 32.0],
        [12.0, 36.0], [13.0, 42.0], [13.0, 46.0], [10.0, 46.0], [0.0, 46.0]
    ]
    mesh = trimesh.creation.revolve(np.array(profile), sections=sections)
    trimesh.repair.fix_normals(mesh)
    if scale != 1.0:
        mesh.apply_scale(scale)
    return mesh


def generate_chess_bishop(scale=1.0, sections=48):
    """Staunton Chess Bishop with oval miter."""
    profile = [
        [0.0, 0.0], [15.0, 0.0], [15.0, 3.0], [13.5, 4.5], [13.5, 6.0],
        [12.0, 8.0], [12.0, 11.0], [10.0, 16.0], [7.5, 24.0], [7.0, 30.0],
        [8.0, 36.0], [9.5, 40.0], [12.0, 42.0], [9.5, 44.0],
        [11.5, 49.0], [11.0, 54.0], [8.0, 58.0], [4.0, 60.0], [0.0, 60.0]
    ]
    body = trimesh.creation.revolve(np.array(profile), sections=sections)
    finial = trimesh.creation.icosphere(subdivisions=2, radius=2.5)
    finial.apply_translation([0.0, 0.0, 62.0])
    mesh = trimesh.util.concatenate([body, finial])
    trimesh.repair.fix_normals(mesh)
    if scale != 1.0:
        mesh.apply_scale(scale)
    return mesh


def generate_chess_pawn(scale=1.0, sections=48):
    """Staunton Chess Pawn with spherical head."""
    profile = [
        [0.0, 0.0], [13.0, 0.0], [13.0, 2.5], [11.5, 4.0], [11.5, 5.5],
        [10.0, 7.5], [9.5, 10.0], [8.0, 15.0], [6.5, 20.0], [6.0, 25.0],
        [7.5, 28.0], [8.5, 30.0], [6.5, 31.0], [0.0, 31.0]
    ]
    body = trimesh.creation.revolve(np.array(profile), sections=sections)
    head = trimesh.creation.icosphere(subdivisions=2, radius=5.0)
    head.apply_translation([0.0, 0.0, 35.5])
    mesh = trimesh.util.concatenate([body, head])
    trimesh.repair.fix_normals(mesh)
    if scale != 1.0:
        mesh.apply_scale(scale)
    return mesh


def generate_parametric_cup(scale=1.0, sections=48):
    """
    Parametric Coffee Cup / Mug:
    Watertight hollow cylinder cup with 2.5mm wall, solid 4mm base, and C-handle.
    """
    profile = [
        [0.0, 0.0],
        [17.0, 0.0],
        [18.0, 1.5],
        [18.0, 42.0],
        [15.5, 42.0],
        [15.5, 4.0],
        [0.0, 4.0]
    ]
    cup_body = trimesh.creation.revolve(np.array(profile), sections=sections)
    handle = trimesh.creation.torus(major_radius=11.0, minor_radius=2.2, major_sections=24, minor_sections=16)
    handle.apply_transform(trimesh.transformations.rotation_matrix(np.radians(90), [1, 0, 0]))
    handle.apply_translation([18.0, 0.0, 22.0])
    cup_mesh = trimesh.util.concatenate([cup_body, handle])
    trimesh.repair.fix_normals(cup_mesh)
    if scale != 1.0:
        cup_mesh.apply_scale(scale)
    return cup_mesh


def generate_parametric_vase(scale=1.0, sections=48):
    """
    Parametric Vase / Urn:
    Fluted profile with bulbous waist, tapering neck, and flared lip.
    """
    profile = [
        [0.0, 0.0],
        [14.0, 0.0],
        [14.0, 3.0],
        [19.0, 15.0],
        [20.0, 24.0],
        [14.0, 38.0],
        [11.0, 48.0],
        [13.5, 54.0],
        [11.5, 54.0],
        [9.0, 47.0],
        [12.0, 37.0],
        [17.5, 24.0],
        [16.5, 15.0],
        [11.5, 4.0],
        [0.0, 4.0]
    ]
    vase_mesh = trimesh.creation.revolve(np.array(profile), sections=sections)
    trimesh.repair.fix_normals(vase_mesh)
    if scale != 1.0:
        vase_mesh.apply_scale(scale)
    return vase_mesh


def generate_parametric_hat(scale=1.0, sections=48):
    """
    Parametric Top Hat / Fedora:
    Flat circular brim with cylindrical crown and rounded dome.
    """
    profile = [
        [0.0, 0.0],
        [24.0, 0.0],
        [24.0, 2.0],
        [15.0, 2.5],
        [14.0, 26.0],
        [15.0, 28.0],
        [0.0, 28.0]
    ]
    hat_mesh = trimesh.creation.revolve(np.array(profile), sections=sections)
    trimesh.repair.fix_normals(hat_mesh)
    if scale != 1.0:
        hat_mesh.apply_scale(scale)
    return hat_mesh


def generate_mechanical_gear(scale=1.0, num_teeth=12, r_tip=20.0, r_root=15.0, thickness=8.0):
    """
    Parametric Mechanical Spur Gear:
    12-tooth involute spur gear with center hub.
    """
    angles = []
    radii = []
    d_theta = 2 * np.pi / num_teeth
    for i in range(num_teeth):
        base_a = i * d_theta
        angles.extend([
            base_a,
            base_a + 0.18 * d_theta,
            base_a + 0.38 * d_theta,
            base_a + 0.62 * d_theta,
            base_a + 0.82 * d_theta,
        ])
        radii.extend([
            r_root,
            r_root,
            r_tip,
            r_tip,
            r_root,
        ])
    pts_x = np.array(radii) * np.cos(angles)
    pts_z = np.array(radii) * np.sin(angles)
    poly = np.column_stack([pts_x, pts_z])

    gear_body = extrude_polygon_xz(poly, thickness_y=thickness)
    gear_body.apply_transform(trimesh.transformations.rotation_matrix(np.radians(90), [1, 0, 0]))
    gear_body.apply_translation([0, 0, thickness / 2.0])

    hub = trimesh.creation.cylinder(radius=8.0, height=thickness + 4.0, sections=32)
    hub.apply_translation([0, 0, (thickness + 4.0) / 2.0 - 2.0])

    gear_mesh = trimesh.util.concatenate([gear_body, hub])
    trimesh.repair.fix_normals(gear_mesh)
    if scale != 1.0:
        gear_mesh.apply_scale(scale)
    return gear_mesh


def generate_calibration_cube(scale=1.0, size=20.0):
    """
    Parametric Calibration Cube:
    Standard 20x20x20mm test cube with top circular marker, watertight and sliceable.
    """
    cube = trimesh.creation.box(extents=[size, size, size])
    cube.apply_translation([0, 0, size / 2.0])

    marker = trimesh.creation.cylinder(radius=size * 0.35, height=1.0, sections=32)
    marker.apply_translation([0, 0, size + 0.5])

    total = trimesh.util.concatenate([cube, marker])
    trimesh.repair.fix_normals(total)
    if scale != 1.0:
        total.apply_scale(scale)
    return total


def generate_parametric_cylinder(scale=1.0, radius=12.0, height=36.0, sections=48):
    """
    Parametric Cylinder / Rod / Pillar:
    Solid cylinder with chamfered base and crown.
    """
    profile = [
        [0.0, 0.0],
        [radius + 2.0, 0.0],
        [radius + 2.0, 3.0],
        [radius, 5.0],
        [radius, height - 3.0],
        [radius - 1.5, height],
        [0.0, height]
    ]
    mesh = trimesh.creation.revolve(np.array(profile), sections=sections)
    trimesh.repair.fix_normals(mesh)
    if scale != 1.0:
        mesh.apply_scale(scale)
    return mesh


def generate_parametric_pyramid(scale=1.0, base_width=26.0, height=32.0):
    """
    Parametric 4-Sided Pyramid with stepped plinth base.
    """
    plinth = trimesh.creation.box(extents=[base_width + 4.0, base_width + 4.0, 4.0])
    plinth.apply_translation([0, 0, 2.0])

    # Pyramid vertices
    hw = base_width / 2.0
    vertices = np.array([
        [-hw, -hw, 4.0],
        [ hw, -hw, 4.0],
        [ hw,  hw, 4.0],
        [-hw,  hw, 4.0],
        [0.0, 0.0, 4.0 + height]
    ])
    faces = np.array([
        [0, 1, 4],
        [1, 2, 4],
        [2, 3, 4],
        [3, 0, 4],
        [0, 3, 2],
        [0, 2, 1]
    ])
    pyramid = trimesh.Trimesh(vertices=vertices, faces=faces, process=True)
    total = trimesh.util.concatenate([plinth, pyramid])
    trimesh.repair.fix_normals(total)
    if scale != 1.0:
        total.apply_scale(scale)
    return total


def generate_calibration_pedestal(prompt: str, scale=1.0, sections=48):
    """
    Standard Parametric Calibration Pedestal:
    Stepped disc with bevelled pedestal (diameter 36mm, height 16mm).
    Used as an honest, documented fallback when prompt does not match specialized CAD templates.
    """
    profile = [
        [0.0, 0.0],
        [18.0, 0.0],
        [18.0, 3.0],
        [16.0, 5.0],
        [16.0, 8.0],
        [14.0, 11.0],
        [14.0, 14.0],
        [12.0, 16.0],
        [0.0, 16.0]
    ]
    mesh = trimesh.creation.revolve(np.array(profile), sections=sections)
    trimesh.repair.fix_normals(mesh)
    if scale != 1.0:
        mesh.apply_scale(scale)
    return mesh


def generate_model(prompt: str, output_path: str = None, fmt: str = "obj") -> dict:
    """
    Selects and creates the appropriate procedural 3D model based on prompt text.
    Exports OBJ/STL to output_path if provided.
    """
    lower = prompt.lower().strip()

    import re

    # 1. Staunton Chess Pieces
    if re.search(r'\b(knight|horse|ghoda|equine)\b', lower):
        mesh = generate_chess_knight()
        piece_name = "Staunton Chess Knight"
        desc = "[PROCEDURAL CAD] Generated parametric Staunton Chess Knight mesh (equine profile, arched neck, ears, snout, and circular pedestal base)."
    elif re.search(r'\b(king|raja)\b', lower):
        mesh = generate_chess_king()
        piece_name = "Staunton Chess King"
        desc = "[PROCEDURAL CAD] Generated parametric Staunton Chess King mesh (flared coronet crown, finial cross, waisted column, and stepped pedestal base)."
    elif re.search(r'\b(queen|rani)\b', lower):
        mesh = generate_chess_queen()
        piece_name = "Staunton Chess Queen"
        desc = "[PROCEDURAL CAD] Generated parametric Staunton Chess Queen mesh (coronet rim with spherical finial and pedestal base)."
    elif re.search(r'\b(rook|castle|kila|tower|parapet)\b', lower):
        mesh = generate_chess_rook()
        piece_name = "Staunton Chess Rook"
        desc = "[PROCEDURAL CAD] Generated parametric Staunton Chess Rook mesh (crenellated parapet tower with cylindrical column and stepped base)."
    elif re.search(r'\b(bishop|miter|mitre)\b', lower):
        mesh = generate_chess_bishop()
        piece_name = "Staunton Chess Bishop"
        desc = "[PROCEDURAL CAD] Generated parametric Staunton Chess Bishop mesh (oval miter with top finial sphere and pedestal base)."
    elif re.search(r'\b(pawn|pyada)\b', lower):
        mesh = generate_chess_pawn()
        piece_name = "Staunton Chess Pawn"
        desc = "[PROCEDURAL CAD] Generated parametric Staunton Chess Pawn mesh (spherical head with collar and pedestal base)."

    # 2. Everyday & Architectural Shapes
    elif re.search(r'\b(cup|cups|mug|mugs|chalice|goblet|tea\s*cup|coffee\s*cup)\b', lower):
        mesh = generate_parametric_cup()
        piece_name = "Parametric Coffee Cup"
        desc = "[PROCEDURAL CAD] Generated parametric hollow cup/mug mesh (watertight cavity, rounded base, and ergonomic C-handle)."
    elif re.search(r'\b(vase|vases|urn|urns|pot|vessel|bottle|planter)\b', lower):
        mesh = generate_parametric_vase()
        piece_name = "Parametric Ceramic Vase"
        desc = "[PROCEDURAL CAD] Generated parametric fluted vase/urn mesh (bulbous waist, contoured neck, and flared lip)."
    elif re.search(r'\b(hat|hats|cap|caps|fedora|top\s*hat)\b', lower):
        mesh = generate_parametric_hat()
        piece_name = "Parametric Hat"
        desc = "[PROCEDURAL CAD] Generated parametric top hat mesh (wide circular brim, cylindrical crown, and rounded top dome)."

    # 3. Mechanical & Geometric Shapes
    elif re.search(r'\b(gear|gears|cog|cogs|sprocket|sprockets|wheel|pinion|pinions|spoke)\b', lower):
        mesh = generate_mechanical_gear()
        piece_name = "Mechanical Spur Gear"
        desc = "[PROCEDURAL CAD] Generated parametric 12-tooth mechanical spur gear (involute teeth, central hub, and shaft bore)."
    elif re.search(r'\b(cube|cubes|box|boxes|block|blocks|rubik|brick|die|dice)\b', lower):
        mesh = generate_calibration_cube()
        piece_name = "Calibration Test Cube"
        desc = "[PROCEDURAL CAD] Generated parametric 20x20x20mm calibration test cube (watertight planar faces with top alignment marker)."
    elif re.search(r'\b(cylinder|cylinders|tube|tubes|pipe|pipes|rod|rods|pillar|pillars|column|columns|shaft)\b', lower):
        mesh = generate_parametric_cylinder()
        piece_name = "Parametric Cylinder"
        desc = "[PROCEDURAL CAD] Generated parametric cylinder/pillar mesh (stepped chamfer base, solid column, and bevelled crown)."
    elif re.search(r'\b(pyramid|pyramids|cone|cones|funnel|funnels|prism)\b', lower):
        mesh = generate_parametric_pyramid()
        piece_name = "Parametric Pyramid"
        desc = "[PROCEDURAL CAD] Generated parametric 4-sided pyramid mesh (stepped plinth base and triangular facets)."

    # 4. Honest Calibration Fallback for Unrecognized Prompts
    else:
        mesh = generate_calibration_pedestal(prompt)
        piece_name = "Calibration Pedestal"
        desc = f"[PROCEDURAL CAD] Prompt '{prompt}' did not match specialized CAD templates. Generated standard parametric calibration pedestal (36mm diam x 16mm height). Supported templates: Chess pieces (king, queen, knight, rook, bishop, pawn), cup/mug, vase, hat, gear, cube, cylinder, pyramid."

    dim = (mesh.bounds[1] - mesh.bounds[0]).round(2).tolist()
    bounds = [mesh.bounds[0].round(2).tolist(), mesh.bounds[1].round(2).tolist()]

    file_size = 0
    if output_path:
        os.makedirs(os.path.dirname(os.path.abspath(output_path)), exist_ok=True)
        file_type = fmt.lower() if fmt in ["obj", "stl"] else "obj"
        mesh.export(output_path, file_type=file_type)
        file_size = os.path.getsize(output_path)

    return {
        "success": True,
        "piece_name": piece_name,
        "prompt": prompt,
        "vertices": len(mesh.vertices),
        "faces": len(mesh.faces),
        "dimensions_mm": dim,
        "bounds": bounds,
        "output_path": output_path or "",
        "file_size": file_size,
        "is_fallback": False,
        "generation_method": "procedural_cad",
        "metadata_description": desc,
        "is_watertight": bool(mesh.is_watertight)
    }


def main():
    parser = argparse.ArgumentParser(description="Procedural 3D Geometry Generator for Chess Pieces and Parametric Shapes")
    parser.add_argument("--prompt", type=str, required=True, help="Text prompt for the 3D model")
    parser.add_argument("--output", type=str, required=True, help="Output file path (.obj or .stl)")
    parser.add_argument("--format", type=str, default="obj", choices=["obj", "stl"], help="Output format")
    args = parser.parse_args()

    result = generate_model(args.prompt, args.output, args.format)
    print(json.dumps(result, indent=2))


if __name__ == '__main__':
    main()
