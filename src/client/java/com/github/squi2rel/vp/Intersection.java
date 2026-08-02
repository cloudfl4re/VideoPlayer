package com.github.squi2rel.vp;

import com.github.squi2rel.vp.video.ClientVideoScreen;
import com.github.squi2rel.vp.video.ScreenSurface;
import org.joml.Vector3d;
import org.joml.Vector3f;

public class Intersection {
    public static Result intersect(Vector3f lineStart, Vector3f lineEnd, ClientVideoScreen player) {
        return intersect(new Vector3d(lineStart), new Vector3d(lineEnd), player);
    }

    public static Result intersect(Vector3d lineStart, Vector3d lineEnd, ClientVideoScreen player) {
        Result result = new Result();
        if (player.surface == ScreenSurface.SPHERE_360 && player.spherePreset) {
            intersectGeometry(lineStart, lineEnd, player, result);
            if (player.sphereSkybox) {
                return result;
            }
            Result sphere = new Result();
            if (intersectSphere(lineStart, lineEnd, player, sphere.precisePoint)) {
                sphere.setHit(player, lineStart);
                if (!result.intersects || sphere.preciseDistance < result.preciseDistance) {
                    result = sphere;
                }
            }
            return result;
        }
        intersectGeometry(lineStart, lineEnd, player, result);
        return result;
    }

    private static void intersectGeometry(Vector3d lineStart, Vector3d lineEnd, ClientVideoScreen player, Result result) {
        try {
            if (player.geometry().intersectsRay(lineStart, lineEnd, result.precisePoint)) {
                result.setHit(player, lineStart);
            }
        } catch (IllegalArgumentException ignored) {
        }
    }

    private static boolean intersectSphere(Vector3d lineStart, Vector3d lineEnd, ClientVideoScreen screen, Vector3d intersection) {
        Vector3f center = screen.sphereCenter == null ? new Vector3f() : screen.sphereCenter;
        double radius = screen.sphereRadius;
        if (!Double.isFinite(radius) || radius <= 0) return false;

        Vector3d d = new Vector3d(lineEnd).sub(lineStart);
        double a = d.dot(d);
        if (a <= 0.000001) return false;
        Vector3d m = new Vector3d(lineStart).sub(center.x, center.y, center.z);
        double b = 2.0 * m.dot(d);
        double c = m.dot(m) - radius * radius;
        double disc = b * b - 4.0 * a * c;
        if (disc < 0) return false;

        double sqrt = Math.sqrt(disc);
        double t1 = (-b - sqrt) / (2.0 * a);
        double t2 = (-b + sqrt) / (2.0 * a);
        double t = Double.POSITIVE_INFINITY;
        if (t1 >= 0 && t1 <= 1) t = t1;
        if (t2 >= 0 && t2 <= 1) t = Math.min(t, t2);
        if (!Double.isFinite(t)) return false;
        intersection.set(d).mul(t).add(lineStart);
        return true;
    }

    public static class Result {
        public boolean intersects;
        public Vector3f point;
        public float distance;
        public Vector3d precisePoint;
        public double preciseDistance;
        public ClientVideoScreen screen;

        public Result() {
            this.intersects = false;
            this.point = new Vector3f();
            this.distance = 0.0f;
            this.precisePoint = new Vector3d();
            this.preciseDistance = 0.0;
        }

        private void setHit(ClientVideoScreen screen, Vector3d lineStart) {
            this.preciseDistance = new Vector3d(precisePoint).sub(lineStart).length();
            this.point.set(precisePoint);
            this.distance = (float) preciseDistance;
            this.intersects = true;
            this.screen = screen;
        }
    }
}
