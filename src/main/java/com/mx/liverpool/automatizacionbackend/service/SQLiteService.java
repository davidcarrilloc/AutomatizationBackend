package com.mx.liverpool.automatizacionbackend.service;

import com.mx.liverpool.automatizacionbackend.model.TxPorMinuto;
import com.mx.liverpool.automatizacionbackend.repository.SQLiteRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class SQLiteService {
    private final SQLiteRepository sqLiteRepository;

    @Autowired
    public SQLiteService(SQLiteRepository sqLiteRepository) {
        this.sqLiteRepository = sqLiteRepository;
    }

    public Map<String,String> crearTabla() {
        sqLiteRepository.crearTabla();
        return Map.of("status", "success");
    }

    public void insertarInformacion(List<TxPorMinuto> txPorMinuto) {
        boolean hasApp = false;
        boolean hasVentel = false;
        boolean hasWebWap = false;
        for (TxPorMinuto tx : txPorMinuto) {
            if (tx.getCanal() != null && tx.getCanal().equals("App")) hasApp = true;
            if (tx.getCanal() != null && tx.getCanal().equals("Ventel")) hasVentel = true;
            if (tx.getCanal() != null && tx.getCanal().equals("Web-Wap")) hasWebWap = true;
        }

        TxPorMinuto tx = new TxPorMinuto();
        if (!hasApp) tx.setCanal("App");
        if (!hasVentel) tx.setCanal("Ventel");
        if (!hasWebWap) tx.setCanal("Web-Wap");

        if (!hasWebWap || !hasVentel || !hasApp) {
            tx.setTruncdate(txPorMinuto.getFirst().getTruncdate());
            tx.setTransacciones(0);
            tx.setTotalCobrado(0.0);
            tx.setSitio(null);
        }

        txPorMinuto.add(tx);
        sqLiteRepository.insertarInformacion(txPorMinuto);
    }
}
