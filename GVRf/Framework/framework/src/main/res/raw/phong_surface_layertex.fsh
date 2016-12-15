#ifdef HAS_ambientTexture
in vec2 ambient_coord;
#endif

#ifdef HAS_opacityTexture
in vec2 opacity_coord;
#endif

#ifdef HAS_specularTexture
in vec2 specular_coord;
#endif

#ifdef HAS_emissiveTexture
in vec2 emissive_coord;
#endif

#ifdef HAS_normalTexture
in vec2 normal_coord;
#endif

#ifdef HAS_lightMapTexture
in vec2 lightmap_coord;
#endif

#ifdef HAS_ambientTexture1
in vec2 ambient_coord1;
uniform sampler2D ambientTexture1;
#endif

#ifdef HAS_diffuseTexture1
in vec2 diffuse_coord1;
uniform sampler2D diffuseTexture1;
#endif

#ifdef HAS_specularTexture1
in vec2 specular_coord1;
uniform sampler2D specularTexture1;
#endif

#ifdef HAS_emissiveTexture1
in vec2 emissive_coord1;
uniform sampler2D emissiveTexture1;
#endif

#ifdef HAS_lightMapTexture1
in vec2 lightmap_coord1;
#endif

uniform sampler2D ambientTexture;
uniform sampler2D diffuseTexture;
uniform sampler2D specularTexture;
uniform sampler2D opacityTexture;
uniform sampler2D lightmapTexture;
uniform sampler2D emissiveTexture;
uniform sampler2D normalTexture;

#ifdef HAS_ambientTexture1_blendop
uniform int ambientTexture1_blendop;
#endif

#ifdef HAS_diffuseTexture1_blendop
uniform int diffuseTexture1_blendop;
#endif

#ifdef HAS_specularTexture1_blendop
uniform int specularTexture1_blendop;
#endif

#ifdef HAS_emissiveTexture1_blendop
uniform int emissiveTexture1_blendop;
#endif

#ifdef HAS_lightMapTexture1_blendop
uniform sampler2D lightMapTexture1;
uniform int lightMapTexture1_blendop;
#endif

uniform vec2 u_lightmap_offset;
uniform vec2 u_lightmap_scale;

struct Surface
{
   vec3 viewspaceNormal;
   vec4 ambient;
   vec4 diffuse;
   vec4 specular;
   vec4 emission;
};

#define BLEND_MULTIPLY 0
#define BLEND_ADD 1
#define BLEND_SUBTRACT 2
#define BLEND_DIVIDE 3
#define BLEND_SMOOTH_ADD 4
#define BLEND_SIGNED_ADD 5

//
// Blends two input colors based on a blend operation.
//
vec4 BlendColors(vec4 color1, vec4 color2, int blendop)
{
    vec4 color;

    if (blendop == BLEND_MULTIPLY)
    {
        return color1 * color2;
    }
    if (blendop == BLEND_ADD)
    {
        color = color1 + color2;
        return clamp(color, 0.0, 1.0);
    }
    if (blendop == BLEND_SUBTRACT)
    {
        color = color1 - color2;
        return clamp(color, 0.0, 1.0);
    }
    if (blendop == BLEND_DIVIDE)
    {
        return color1 / color2;
    }
    if (blendop == BLEND_SMOOTH_ADD)
    {
        color = (color1 + color2) - (color1 * color2);
        return clamp(color, 0.0, 1.0);
    }
    if (blendop == BLEND_SIGNED_ADD)
    {
        color =  color1 + (color2 - 0.5);
        return clamp(color, 0.0, 1.0);
    }
    return color1;
}

Surface @ShaderName()
{
	vec4 diffuse = diffuse_color;
	vec4 emission = emissive_color;
	vec4 specular = specular_color;
	vec4 ambient = ambient_color;
	vec3 viewspaceNormal;
	vec4 temp;

#ifdef HAS_ambientTexture
	ambient *= texture(ambientTexture, ambient_coord.xy);
#endif
#ifdef HAS_ambientTexture1_blendop
    temp = texture(ambientTexture1, ambient_coord1.xy);
	ambient = BlendColors(ambient, temp, ambientTexture1_blendop);
#endif

#ifdef HAS_diffuseTexture
	diffuse *= texture(diffuseTexture, diffuse_coord.xy);
#endif
#ifdef HAS_diffuseTexture1_blendop
    temp = texture(diffuseTexture1, diffuse_coord1.xy);
	diffuse = BlendColors(diffuse, temp, diffuseTexture1_blendop);
#endif

#ifdef HAS_opacityTexture
	diffuse.a *= texture(opacityTexture, opacity_coord.xy).a;
#endif
diffuse.xyz *= diffuse.a;

#ifdef HAS_specularTexture
	specular *= texture(specularTexture, specular_coord.xy);
#endif
#ifdef HAS_specularTexture1_blendop
    temp = texture(specularTexture1, specular_coord1.xy);
	specular = BlendColors(specular, temp, specularTexture1_blendop);
#endif

#ifdef HAS_emissiveTexture
	emission = texture(emissiveTexture, emissive_coord.xy);
#endif
#ifdef HAS_emissiveTexture1_blendop
    temp = texture(emissiveTexture1, emissive_coord1.xy);
    emission = BlendColors(emission, temp, emissiveTexture1_blendop);
#endif

#ifdef HAS_normalTexture
	viewspaceNormal = texture(normalTexture, normal_coord.xy).xyz * 2.0 - 1.0;
#else
	viewspaceNormal = viewspace_normal;
#endif

#ifdef HAS_lightMapTexture
	vec2 lcoord = (lightmap_coord * u_lightMap_scale) + u_lightMap_offset;
	diffuse *= texture(lightMapTexture, vec2(lcoord.x, 1 - lcoord.y));
	#ifdef HAS_lightMapTexture1
		lcoord = (lightmap_coord1 * u_lightMap_scale) + u_lightMap_offset;
    	diffuse = BlendColors(diffuse, texture(lightMapTexture1, vec2(lcoord.x, 1 - lcoord.y), lightMapTexture1_blendop);
    #endif
	return Surface(viewspaceNormal, ambient, vec4(0.0, 0.0, 0.0, 0.0), specular, emission);
#else
	return Surface(viewspaceNormal, ambient, diffuse, specular, emission);
#endif
}
