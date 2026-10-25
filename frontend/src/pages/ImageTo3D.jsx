import { Navigate } from 'react-router-dom'

/**
 * ImageTo3D is now unified into the Meshy AI-inspired 3D Creation Studio at /generate?mode=image.
 */
export default function ImageTo3D() {
    return <Navigate to="/generate?mode=image" replace />
}
