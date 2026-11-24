package org.lab.lsh;

public record Vector3D(double x, double y, double z) {
    public double dot(Vector3D other) {
        return this.x * other.x + this.y * other.y + this.z * other.z;
    }
}