package com.mx.liverpool.automatizacionbackend.service;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Service
public class ExcelService {
    public List<Map<Integer,String>> fromExcelToListOfRows(MultipartFile file, String numSheet, String numCol) {
        int[] habilitedCols = Arrays.stream(numCol.split(",")).mapToInt(Integer::parseInt).toArray();
        List<Map<Integer,String>> habilitedCells = new ArrayList<>();

        try (InputStream is = file.getInputStream();
             Workbook workbook = new XSSFWorkbook(is)) {
            Sheet sheet = workbook.getSheetAt(Integer.parseInt(numSheet));

            for (Row row : sheet) {
                for (int colIndex : habilitedCols) {
                    Cell cell = row.getCell(colIndex);
                    if (cell != null) {
                        habilitedCells.add(Map.of(colIndex,cell.getStringCellValue()));
                    }
                }

                if (habilitedCells.isEmpty()) {
                    throw new IllegalArgumentException("La fila " + row.getRowNum() + " no tiene datos en columnas habilitadas.");
                }
            }

            return habilitedCells;
        } catch (Exception e) {
            throw new RuntimeException("Error al procesar el archivo Excel: " + e.getMessage());
        }
    }
}
