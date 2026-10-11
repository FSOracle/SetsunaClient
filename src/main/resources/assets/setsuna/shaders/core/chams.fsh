#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

in float sphericalVertexDistance;
in float cylindricalVertexDistance;

#ifdef PER_FACE_LIGHTING
in vec4 vertexPerFaceColorBack;
in vec4 vertexPerFaceColorFront;
#else
in vec4 vertexColor;
#endif

#ifndef EMISSIVE
in vec4 lightMapColor;
#endif

out vec4 fragColor;

void main() {
#ifdef PER_FACE_LIGHTING
    vec4 color = gl_FrontFacing ? vertexPerFaceColorFront : vertexPerFaceColorBack;
#else
    vec4 color = vertexColor;
#endif

    color *= ColorModulator;
#ifndef EMISSIVE
    color *= lightMapColor;
#endif

    fragColor = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance,
        FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart,
        FogRenderDistanceEnd, FogColor);
}
