"""
3D Wireframe Engine for PrintGuard AI Video Generator
Renders a rotating 3D wireframe model with perspective projection,
depth-based glow shading, vertex nodes, and a dynamic laser scanning plane.
"""

import math
import numpy as np
from PIL import Image, ImageDraw

class Wireframe3D:
    def __init__(self, model_type="polyhedron"):
        self.vertices, self.edges = self._build_model(model_type)
        
    def _build_model(self, model_type):
        """Constructs 3D geometry vertices (N, 3) and edge pairs."""
        # Golden ratio for icosahedron
        phi = (1.0 + math.sqrt(5.0)) / 2.0
        
        # Outer Icosahedron vertices
        outer_v = [
            [-1,  phi,  0], [ 1,  phi,  0], [-1, -phi,  0], [ 1, -phi,  0],
            [ 0, -1,  phi], [ 0,  1,  phi], [ 0, -1, -phi], [ 0,  1, -phi],
            [ phi,  0, -1], [ phi,  0,  1], [-phi,  0, -1], [-phi,  0,  1]
        ]
        # Normalize to radius 1.0
        outer_v = np.array(outer_v, dtype=np.float32)
        outer_v = outer_v / np.linalg.norm(outer_v[0]) * 1.0
        
        # Find icosahedron edges (distance between adjacent vertices is ~1.05)
        outer_edges = []
        n_outer = len(outer_v)
        for i in range(n_outer):
            for j in range(i + 1, n_outer):
                dist = np.linalg.norm(outer_v[i] - outer_v[j])
                if abs(dist - 1.05146) < 0.15:
                    outer_edges.append((i, j))
                    
        # Inner Cube (radius 0.5) to give complex internal structure
        cube_v = []
        for x in [-0.42, 0.42]:
            for y in [-0.42, 0.42]:
                for z in [-0.42, 0.42]:
                    cube_v.append([x, y, z])
        cube_v = np.array(cube_v, dtype=np.float32)
        offset = len(outer_v)
        
        cube_edges = []
        for i in range(8):
            for j in range(i + 1, 8):
                diff = np.abs(cube_v[i] - cube_v[j])
                if np.count_nonzero(np.isclose(diff, 0.0, atol=0.01)) == 2:
                    cube_edges.append((offset + i, offset + j))
                    
        # Connect inner cube to outer vertices for technical cage look
        cross_edges = []
        for i in range(8):
            closest_outer = np.argmin(np.linalg.norm(outer_v - cube_v[i], axis=1))
            cross_edges.append((offset + i, closest_outer))
            
        all_vertices = np.vstack([outer_v, cube_v])
        all_edges = outer_edges + cube_edges + cross_edges
        return all_vertices, all_edges

    def render(self, draw, center_x, center_y, scale=280.0, yaw=0.0, pitch=0.0, roll=0.0,
               laser_progress=None, cyan_color=(0, 240, 255), violet_color=(139, 92, 246)):
        """
        Renders the wireframe into a PIL ImageDraw context.
        yaw, pitch, roll in radians.
        laser_progress: float 0.0 to 1.0 (scans top to bottom) or None.
        """
        # Rotation matrices
        cy, sy = math.cos(yaw), math.sin(yaw)
        cp, sp = math.cos(pitch), math.sin(pitch)
        cr, sr = math.cos(roll), math.sin(roll)
        
        R_yaw = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]], dtype=np.float32)
        R_pitch = np.array([[1, 0, 0], [0, cp, -sp], [0, sp, cp]], dtype=np.float32)
        R_roll = np.array([[cr, -sr, 0], [sr, cr, 0], [0, 0, 1]], dtype=np.float32)
        R = R_roll @ R_pitch @ R_yaw
        
        rotated = self.vertices @ R.T
        
        # Perspective projection
        # Camera is at distance Z = 3.5
        cam_dist = 3.2
        proj_pts = []
        depths = []
        for v in rotated:
            z = v[2] + cam_dist
            persp = scale / max(z, 0.5)
            x_2d = center_x + v[0] * persp
            y_2d = center_y - v[1] * persp
            proj_pts.append((x_2d, y_2d))
            depths.append(v[2])
            
        # Draw edges sorted or with depth fade
        # Laser plane Y height in 3D object coordinates (-1.2 to 1.2)
        laser_y = None
        if laser_progress is not None:
            laser_y = 1.1 - 2.2 * (0.5 - 0.5 * math.cos(laser_progress * 2 * math.pi))
            
        for i, j in self.edges:
            p1 = proj_pts[i]
            p2 = proj_pts[j]
            z_mid = (depths[i] + depths[j]) / 2.0  # -1.0 to 1.0
            
            # Depth alpha
            alpha_factor = 0.4 + 0.6 * ((z_mid + 1.0) / 2.0)
            
            # Check proximity to laser scan plane
            is_lasered = False
            if laser_y is not None:
                y_mid_3d = (rotated[i][1] + rotated[j][1]) / 2.0
                if abs(y_mid_3d - laser_y) < 0.22:
                    is_lasered = True
                    
            if is_lasered:
                r = int(cyan_color[0] * alpha_factor * 1.2)
                g = int(cyan_color[1] * alpha_factor * 1.2)
                b = int(cyan_color[2] * alpha_factor * 1.2)
                edge_color = (min(255, r + 50), min(255, g), min(255, b))
                line_w = 3
            else:
                r = int(cyan_color[0] * 0.7 + violet_color[0] * 0.3)
                g = int(cyan_color[1] * 0.7 + violet_color[1] * 0.3)
                b = int(cyan_color[2] * 0.7 + violet_color[2] * 0.3)
                edge_color = (int(r * alpha_factor), int(g * alpha_factor), int(b * alpha_factor))
                line_w = 2 if z_mid > 0 else 1
                
            draw.line([p1, p2], fill=edge_color, width=line_w)
            
        # Draw vertex nodes
        for idx, (px, py) in enumerate(proj_pts):
            z = depths[idx]
            node_r = 3.5 if z > 0 else 2.0
            node_color = (255, 255, 255) if z > 0.3 else cyan_color
            draw.ellipse([px - node_r, py - node_r, px + node_r, py + node_r], fill=node_color)
            
        # Draw laser scanline ellipse projection if active
        if laser_y is not None:
            # Project circular ring at laser_y
            ring_pts = []
            radius = 1.05
            for theta in np.linspace(0, 2 * math.pi, 36, endpoint=False):
                pt_3d = np.array([radius * math.cos(theta), laser_y, radius * math.sin(theta)], dtype=np.float32)
                pt_rot = pt_3d @ R.T
                z = pt_rot[2] + cam_dist
                persp = scale / max(z, 0.5)
                ring_pts.append((center_x + pt_rot[0] * persp, center_y - pt_rot[1] * persp))
            if len(ring_pts) > 2:
                for k in range(len(ring_pts)):
                    p_a = ring_pts[k]
                    p_b = ring_pts[(k + 1) % len(ring_pts)]
                    draw.line([p_a, p_b], fill=(0, 240, 255, 180), width=2)
