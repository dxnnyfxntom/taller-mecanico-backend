package com.taller.mecanica.security;

import com.taller.mecanica.model.entity.Usuario;
import com.taller.mecanica.model.enums.Rol;
import com.taller.mecanica.repository.ConsumoInsumoRepository;
import com.taller.mecanica.repository.EvidenciaFotograficaRepository;
import com.taller.mecanica.repository.InformeVehiculoRepository;
import com.taller.mecanica.repository.UsuarioRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class AccesoOrden {
    private static final Pattern ARCHIVO_DE_ORDEN =
            Pattern.compile("/uploads/(?:evidencias|fichas-tecnicas/informes)/([0-9]+)/[^/]+");
    private static final Pattern FICHA_DE_INSUMO =
            Pattern.compile("/uploads/fichas-tecnicas/insumos/[0-9]+/[^/]+");

    private final UsuarioRepository usuarios;
    private final InformeVehiculoRepository ordenes;
    private final ConsumoInsumoRepository consumos;
    private final EvidenciaFotograficaRepository evidencias;

    public Long filtrarMecanico(Authentication auth, Long filtro) {
        return puedeAdministrarOrden(auth) ? filtro : mecanicoAsignado(auth);
    }

    public boolean orden(Authentication auth, Long id) {
        if (puedeAdministrarOrden(auth)) {
            return true;
        }
        return ordenes.existsByIdAndMecanicoId(id, mecanicoAsignado(auth));
    }

    public boolean consumo(Authentication auth, Long id) {
        return puedeAdministrarOrden(auth) || consumos.buscarInformeId(id)
                .map(ordenId -> orden(auth, ordenId)).orElse(false);
    }

    public boolean evidencia(Authentication auth, Long id) {
        return puedeAdministrarOrden(auth) || evidencias.buscarInformeId(id)
                .map(ordenId -> orden(auth, ordenId)).orElse(false);
    }

    public boolean archivo(Authentication auth, HttpServletRequest request) {
        if (puedeAdministrarOrden(auth)) {
            return true;
        }
        if (!tieneRol(auth, "ROLE_MECANICO")) {
            return false;
        }
        String ruta = request.getRequestURI().substring(request.getContextPath().length());
        var orden = ARCHIVO_DE_ORDEN.matcher(ruta);
        if (orden.matches()) {
            try {
                return orden(auth, Long.valueOf(orden.group(1)));
            } catch (NumberFormatException | AccessDeniedException ex) {
                return false;
            }
        }
        return FICHA_DE_INSUMO.matcher(ruta).matches();
    }

    private boolean puedeAdministrarOrden(Authentication auth) {
        return tieneRol(auth, "ROLE_ADMIN") || tieneRol(auth, "ROLE_RECEPCION");
    }

    private Long mecanicoAsignado(Authentication auth) {
        if (!tieneRol(auth, "ROLE_MECANICO")) {
            throw new AccessDeniedException("No tiene acceso a las ordenes");
        }
        return usuarios.findByUsernameIgnoreCase(auth.getName())
                .filter(u -> u.getRol() == Rol.MECANICO && Boolean.TRUE.equals(u.getActivo()))
                .map(Usuario::getMecanicoId)
                .orElseThrow(() -> new AccessDeniedException("La cuenta no tiene un mecanico asignado"));
    }

    private boolean tieneRol(Authentication auth, String rol) {
        return auth != null && auth.getAuthorities().stream().anyMatch(a -> rol.equals(a.getAuthority()));
    }
}
