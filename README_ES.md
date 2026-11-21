# PerAppScaler

[English](README.md) | [Español](README_ES.md)

**PerAppScaler** es una aplicación open-source para Android que permite ajustar de forma independiente la resolución (downscaling) y el límite de FPS de cada aplicación o juego individual, sin alterar la resolución global de la pantalla (`wm size`).

## Características Principales

- **Sin Root requerido en ejecución:** Funciona nativamente como aplicación privilegiada del sistema (`priv-app`) utilizando el permiso `android.permission.MANAGE_GAME_MODE`.
- **Compatibilidad amplia:** Compatible desde Android 12 (API 31) hasta Android 17+ (API 37).
- **Escalado por Hardware sin latencia:** Emplea las intervenciones de `GameManagerService` gestionadas por SurfaceFlinger y el DPU de Qualcomm/MediaTek. El SystemUI y el resto de apps se mantienen a 1080p nativo mientras el juego se renderiza al porcentaje seleccionado (ej. 70% = 756×1680 px), reduciendo drásticamente el consumo de GPU, temperatura y batería.
- **Multilingüe (i18n):** Selector de idioma integrado compatible con:
  - Español (`es`)
  - English (`en`)
  - Português (`pt`)
  - Bahasa Indonesia (`in`)
  - Tiếng Việt (`vi`)
  - Русский (`ru`)
  - 简体中文 (`zh-CN`)
  - 繁體中文 (`zh-TW`)
  - العربية (`ar` con soporte RTL nativo)
- **Presets Rápidos y Control Manual:**
  - Calidad (100% nativo)
  - Equilibrado (80%)
  - Rendimiento (70%)
  - Ahorro Extremo (50%)
  - Slider continuo de resolución (30% a 100% en saltos de 5%)
  - Limitador de FPS (30, 45, 60, 90, 120 FPS o Ilimitado)
- **Cálculo en tiempo real:** Visualización instantánea de la resolución resultante y del porcentaje de reducción de píxeles procesados.
- **Filtros rápidos:** Filtrado por Todas las Apps, Juegos y Apps Modificadas.

---

## Arquitectura de Permisos Privilegiados

La aplicación declara el permiso de nivel `signature|privileged`:
```xml
<permission name="android.permission.MANAGE_GAME_MODE"/>
<permission name="android.permission.INTERACT_ACROSS_USERS"/>
```

Al incluir el archivo de lista blanca en `/system/etc/permissions/privapp-permissions-com.gamehac.perappscaler.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<permissions>
    <privapp-permissions package="com.gamehac.perappscaler">
        <permission name="android.permission.MANAGE_GAME_MODE"/>
        <permission name="android.permission.INTERACT_ACROSS_USERS"/>
    </privapp-permissions>
</permissions>
```
El `PackageManager` de Android otorga automáticamente los permisos durante el arranque del sistema, sin requerir firma de plataforma ni permisos de superusuario (root) para la aplicación.

---

## Integración en ROMs AOSP

Para compilar PerAppScaler directamente en el árbol de tu ROM (LineageOS, crDroid, EvolutionX, PixelOS, etc.):

1. Clona el repositorio dentro de tu árbol de fuentes (por ejemplo en `packages/apps/PerAppScaler`).
2. En tu archivo `device.mk` agrega:
```makefile
$(call inherit-product, packages/apps/PerAppScaler/perappscaler.mk)
```
3. Compila normalmente (`m PerAppScaler` o compilación completa del sistema).

---

## Instalación alternativa para pruebas (Magisk / KernelSU)

Para probar la app en dispositivos ya rooteados sin recompilar la ROM completa:
1. Se incluye la estructura del módulo en `magisk_module/`.
2. Coloca `PerAppScaler.apk` compilado en `magisk_module/system/priv-app/PerAppScaler/PerAppScaler.apk`.
3. Comprime el contenido de `magisk_module/` en un archivo `.zip` y flashealo en Magisk, KernelSU o APatch.

---

## Licencia

Este proyecto es de código abierto bajo la licencia Apache 2.0.
Desarrollado por **Gamehac_RA** & **Google DeepMind Antigravity**.
