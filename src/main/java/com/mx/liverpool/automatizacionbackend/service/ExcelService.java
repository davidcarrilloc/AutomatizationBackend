package com.mx.liverpool.automatizacionbackend.service;

import com.mx.liverpool.automatizacionbackend.constant.CancelacionAtgMkpConstant;
import com.mx.liverpool.automatizacionbackend.model.AtgMarketplace;
import com.mx.liverpool.automatizacionbackend.model.Dummy;
import lombok.extern.log4j.Log4j2;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

@Service
@Log4j2
public class ExcelService {
    private final DataFormatter dataFormatter;

    @Autowired
    public ExcelService(DataFormatter dataFormatter) {
        this.dataFormatter = dataFormatter;
    }

    public List<Map<Integer,String>> fromExcelToListOfRows(MultipartFile file, String numSheet, String numCol) {
        int[] habilitedCols = Arrays.stream(numCol.split(",")).mapToInt(Integer::parseInt).toArray();
        List<Map<Integer,String>> habilitedCells = new ArrayList<>();

        try (InputStream is = file.getInputStream();
             Workbook workbook = new XSSFWorkbook(is)) {
            Sheet sheet = workbook.getSheetAt(Integer.parseInt(numSheet));

            for (Row row : sheet) {
                Map<Integer, String> cellData = new HashMap<>();
                for (int colIndex : habilitedCols) {
                    Cell cell = row.getCell(colIndex);
                    if (cell != null) {
                        cellData.put(colIndex, dataFormatter.formatCellValue(cell));

                        if (colIndex == 7) {
                            habilitedCells.add(cellData);
                            cellData = new HashMap<>();
                        }
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

    public String crearReporteCancelacion(List<Dummy> dummies, List<AtgMarketplace> atgMarketplaces) {
        String excelId = "";
        for (Dummy dummy : dummies) {
            AtgMarketplace marketplace = atgMarketplaces.stream()
                    .filter(m -> m.getRemision().equals(dummy.getRemision()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("No se encontraron datos de Bridgecore para la remisión: " + dummy.getRemision()));
        }

        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Cancelaciones");

        Row headerRow = sheet.createRow(0);
        int cellIndex = 0;
        for (CancelacionAtgMkpConstant constant : CancelacionAtgMkpConstant.values()) {
            if (CancelacionAtgMkpConstant.DECODE == constant) {
                headerRow.createCell(9).setCellValue(constant.getValues()[0]); cellIndex++;
                headerRow.createCell(10).setCellValue(constant.getValues()[2]); cellIndex++;
                headerRow.createCell(11).setCellValue(constant.getValues()[4]); cellIndex++;
                headerRow.createCell(12).setCellValue(constant.getValues()[6]); cellIndex++;

                headerRow.createCell(15).setCellValue(constant.getValues()[8]);
                headerRow.createCell(16).setCellValue(constant.getValues()[10]);
                headerRow.createCell(21).setCellValue(constant.getValues()[12]);

                headerRow.createCell(70).setCellValue(constant.getValues()[14]);
                headerRow.createCell(71).setCellValue(constant.getValues()[16]);
                headerRow.createCell(72).setCellValue(constant.getValues()[18]);

                headerRow.createCell(75).setCellValue(constant.getValues()[20]);
                headerRow.createCell(76).setCellValue(constant.getValues()[22]);
                headerRow.createCell(77).setCellValue(constant.getValues()[24]);
                continue;
            }

            if (cellIndex == 15) {
                cellIndex++; cellIndex++;
                continue;
            }

            if (cellIndex == 21) {
                cellIndex++;
                continue;
            }

            if (cellIndex == 70 || cellIndex == 75) {
                cellIndex++; cellIndex++; cellIndex++;
                continue;
            }

            headerRow.createCell(cellIndex).setCellValue(constant.name()); cellIndex++;
        }

        int rowNum = 1;
        for (CancelacionAtgMkpConstant constant : CancelacionAtgMkpConstant.values()) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(constant.name());

            break;
            // String valor = String.join(" | ", constant.getValues());
            // row.createCell(1).setCellValue(valor);
        }

        String generadorId = String.valueOf(System.currentTimeMillis());
        String fileName = "ReporteCancelacion_" + generadorId + ".xlsx";
        try (FileOutputStream fileOut = new FileOutputStream(fileName)) {
            workbook.write(fileOut);
            excelId = fileName;
            log.info("Excel creado exitosamente con el nombre: {}", fileName);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                workbook.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return excelId;
    }
}
