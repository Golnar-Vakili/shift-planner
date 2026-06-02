package com.employee.service;

import com.employee.domain.Employee;
import com.employee.domain.ShiftEntry;
import com.employee.dto.VacationRequestDto;
import com.employee.repository.EmployeeRepository;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDPageContentStream.AppendMode;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.awt.Color;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Year;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class DashboardExportService {

	private final ShiftPlanService shiftPlanService;
	private final VacationRequestService vacationRequestService;
	private final EmployeeRepository employeeRepository;

	public DashboardExportService(ShiftPlanService shiftPlanService,
	                              VacationRequestService vacationRequestService,
	                              EmployeeRepository employeeRepository) {
		this.shiftPlanService = shiftPlanService;
		this.vacationRequestService = vacationRequestService;
		this.employeeRepository = employeeRepository;
	}

	public byte[] exportShiftAndHolidayPlanPdf(long employeeId) {
		YearMonth current = YearMonth.now();
		YearMonth next = current.plusMonths(1);
		int year = Year.now().getValue();

		List<ShiftEntry> shiftsCurrent = shiftPlanService.getApprovedShiftEntries(employeeId, current);
		List<ShiftEntry> shiftsNext = shiftPlanService.getApprovedShiftEntries(employeeId, next);
		VacationRequestDto vacation = vacationRequestService.getRequest(employeeId, year);

		Employee employee = employeeRepository.findById(employeeId).orElse(null);
		String employeeName = employee != null
				? ((employee.getDisplayName() != null && !employee.getDisplayName().isBlank()) ? employee.getDisplayName() : employee.getUsername())
				: ("Employee #" + employeeId);

		LocalDate generatedDate = LocalDate.now();
		DateTimeFormatter fmtDate = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);
		DateTimeFormatter fmtMonth = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH);
		String coveredPeriod = current.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH))
				+ " – " + next.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH));

		// Group vacation days by month for clean layout.
		Map<YearMonth, List<LocalDate>> vacationByMonth = (vacation.selectedDates() == null ? List.<LocalDate>of() : vacation.selectedDates())
				.stream()
				.sorted()
				.collect(Collectors.groupingBy(YearMonth::from, LinkedHashMap::new, Collectors.toList()));

		try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			PdfStyle style = new PdfStyle();
			PdfCursor cursor = new PdfCursor(doc);

			// Header
			cursor.ensureSpace(style, 120);
			cursor.drawTitle(style, "Shift & Holiday Plan");
			cursor.drawHeaderMeta(style,
					"Employee: " + employeeName,
					"Generated: " + generatedDate.format(fmtDate),
					"Period: " + coveredPeriod
			);
			cursor.drawDivider(style);

			// Summary cards (simple grid)
			int approvedCount = shiftsCurrent.size() + shiftsNext.size();
			String monthsText = current.getMonth().getDisplayName(java.time.format.TextStyle.SHORT, Locale.ENGLISH)
					+ " / " + next.getMonth().getDisplayName(java.time.format.TextStyle.SHORT, Locale.ENGLISH);
			cursor.drawSectionTitle(style, "Summary");
			cursor.drawSummaryRow(style,
					"Approved shifts", String.valueOf(approvedCount),
					"Vacation days", vacation.selectedCount() + " / " + vacation.entitlementDays(),
					"Covered months", monthsText
			);
			cursor.drawSpacer(style, 6);

			// Shifts tables
			cursor.drawSectionTitle(style, "Approved shifts");
			cursor.drawMonthShiftTable(style, current, shiftsCurrent);
			cursor.drawSpacer(style, 10);
			cursor.drawMonthShiftTable(style, next, shiftsNext);

			// Vacation section grouped by month
			cursor.drawSpacer(style, 18);
			cursor.drawSectionTitle(style, "Vacation");
			cursor.drawBodyText(style, "Request status: " + describeVacationStatus(vacation.status()));
			cursor.drawBodyText(style, "Selected days: " + vacation.selectedCount() + " of " + vacation.entitlementDays());
			cursor.drawSpacer(style, 10);
			cursor.drawVacationTable(style, year, vacationByMonth, fmtDate);

			// Important: close the main content stream before saving/adding footers.
			cursor.close();

			// Footer page numbers
			int totalPages = doc.getNumberOfPages();
			for (int i = 0; i < totalPages; i++) {
				PDPage p = doc.getPage(i);
				try (PDPageContentStream cs = new PDPageContentStream(doc, p, AppendMode.APPEND, true, true)) {
					String left = "Generated by Shift Planner";
					String right = "Page " + (i + 1) + " of " + totalPages;
					float y = style.margin - 18;
					style.drawText(cs, left, style.margin, y, style.fontRegular, 9, style.mutedText);
					float xRight = p.getMediaBox().getWidth() - style.margin - style.textWidth(right, style.fontRegular, 9);
					style.drawText(cs, right, xRight, y, style.fontRegular, 9, style.mutedText);
				}
			}

			doc.save(out);
			return out.toByteArray();
		} catch (Exception e) {
			throw new IllegalStateException("Could not generate PDF export.", e);
		}
	}

	private static class PdfStyle {
		final float margin = 52;
		final float pageWidth = PDRectangle.A4.getWidth();
		final float pageHeight = PDRectangle.A4.getHeight();
		final float contentWidth = pageWidth - margin * 2;

		final PDType1Font fontRegular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
		final PDType1Font fontBold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

		final Color accent = new Color(124, 58, 237); // purple
		final Color text = new Color(20, 20, 20);
		final Color mutedText = new Color(90, 90, 90);
		final Color line = new Color(220, 220, 220);
		final Color headerBg = new Color(245, 243, 255);
		final Color tableHeaderBg = new Color(238, 235, 255);
		final Color tableHeaderText = new Color(54, 38, 120);
		final Color zebra = new Color(250, 250, 250);
		final Color tableOuter = new Color(205, 205, 215);

		float textWidth(String s, PDType1Font font, float size) throws Exception {
			return font.getStringWidth(s) / 1000f * size;
		}

		void drawText(PDPageContentStream cs, String text, float x, float y, PDType1Font font, float size, Color color) throws Exception {
			cs.beginText();
			cs.setNonStrokingColor(color);
			cs.setFont(font, size);
			cs.newLineAtOffset(x, y);
			cs.showText(text != null ? text : "");
			cs.endText();
		}
	}

	private static class PdfCursor {
		private final PDDocument doc;
		private PDPage page;
		private PDPageContentStream cs;
		private float y;

		PdfCursor(PDDocument doc) throws Exception {
			this.doc = doc;
			this.page = new PDPage(PDRectangle.A4);
			doc.addPage(page);
			this.cs = new PDPageContentStream(doc, page);
			this.y = PDRectangle.A4.getHeight() - 52;
		}

		void newPage(PdfStyle s) throws Exception {
			cs.close();
			page = new PDPage(PDRectangle.A4);
			doc.addPage(page);
			cs = new PDPageContentStream(doc, page);
			y = s.pageHeight - s.margin;
		}

		void close() throws Exception {
			if (cs != null) {
				cs.close();
				cs = null;
			}
		}

		void ensureSpace(PdfStyle s, float needed) throws Exception {
			if (y - needed < s.margin + 30) newPage(s);
		}

		void drawSpacer(PdfStyle s, float h) { y -= h; }

		void drawTitle(PdfStyle s, String title) throws Exception {
			s.drawText(cs, title, s.margin, y, s.fontBold, 18, s.text);
			y -= 26;
			// accent line
			cs.setStrokingColor(s.accent);
			cs.setLineWidth(2f);
			cs.moveTo(s.margin, y);
			cs.lineTo(s.margin + 120, y);
			cs.stroke();
			y -= 14;
		}

		void drawHeaderMeta(PdfStyle s, String... lines) throws Exception {
			for (String l : lines) {
				s.drawText(cs, l, s.margin, y, s.fontRegular, 10.5f, s.mutedText);
				y -= 14;
			}
		}

		void drawDivider(PdfStyle s) throws Exception {
			y -= 6;
			cs.setStrokingColor(s.line);
			cs.setLineWidth(1f);
			cs.moveTo(s.margin, y);
			cs.lineTo(s.margin + s.contentWidth, y);
			cs.stroke();
			y -= 16;
		}

		void drawSectionTitle(PdfStyle s, String title) throws Exception {
			ensureSpace(s, 40);
			s.drawText(cs, title, s.margin, y, s.fontBold, 13.5f, s.text);
			y -= 18;
		}

		void drawBodyText(PdfStyle s, String text) throws Exception {
			ensureSpace(s, 18);
			s.drawText(cs, text, s.margin, y, s.fontRegular, 10.5f, s.text);
			y -= 14;
		}

		void drawSummaryRow(PdfStyle s,
		                    String k1, String v1,
		                    String k2, String v2,
		                    String k3, String v3) throws Exception {
			ensureSpace(s, 64);
			float cardH = 50;
			float gap = 10;
			float cardW = (s.contentWidth - gap * 2) / 3f;
			float top = y;

			drawSummaryCard(s, s.margin, top, cardW, cardH, k1, v1);
			drawSummaryCard(s, s.margin + cardW + gap, top, cardW, cardH, k2, v2);
			drawSummaryCard(s, s.margin + (cardW + gap) * 2, top, cardW, cardH, k3, v3);
			y -= (cardH + 10);
		}

		private void drawSummaryCard(PdfStyle s, float x, float topY, float w, float h, String key, String value) throws Exception {
			cs.setNonStrokingColor(s.headerBg);
			cs.addRect(x, topY - h + 10, w, h);
			cs.fill();
			cs.setStrokingColor(s.tableOuter);
			cs.setLineWidth(1f);
			cs.addRect(x, topY - h + 10, w, h);
			cs.stroke();
			s.drawText(cs, key, x + 12, topY - 18, s.fontRegular, 9.5f, s.mutedText);
			s.drawText(cs, value, x + 12, topY - 40, s.fontBold, 15f, s.text);
		}

		void drawMonthShiftTable(PdfStyle s, YearMonth month, List<ShiftEntry> entries) throws Exception {
			ensureSpace(s, 60);
			String monthTitle = month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH));
			s.drawText(cs, monthTitle, s.margin, y, s.fontBold, 12f, s.text);
			y -= 14;

			// Table columns
			float tableX = s.margin;
			float tableW = s.contentWidth;
			float rowH = 18;
			float[] colW = new float[] { 90, 70, 90, 70, 80 }; // Date, Weekday, Time, Duration, Status
			String[] headers = new String[] { "Date", "Weekday", "Time", "Duration", "Status" };

			// Header row
			ensureSpace(s, 34);
			drawTableRowBackground(s, tableX, y, tableW, rowH, s.tableHeaderBg);
			drawTableRowBorders(s, tableX, y, colW, rowH, true);
			drawTableRowText(s, tableX, y, colW, rowH, headers, true);
			y -= rowH;

			if (entries == null || entries.isEmpty()) {
				ensureSpace(s, 28);
				String[] empty = new String[] { "—", "—", "No approved shifts", "—", "—" };
				drawTableRowBackground(s, tableX, y, tableW, rowH, Color.WHITE);
				drawTableRowBorders(s, tableX, y, colW, rowH, false);
				drawTableRowText(s, tableX, y, colW, rowH, empty, false);
				y -= rowH;
				return;
			}

			DateTimeFormatter tf = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH);
			for (int i = 0; i < entries.size(); i++) {
				ensureSpace(s, 26);
				ShiftEntry e = entries.get(i);
				String date = e.getEntryDate().format(DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH));
				String weekday = e.getEntryDate().getDayOfWeek().getDisplayName(java.time.format.TextStyle.SHORT, Locale.ENGLISH);
				String time = e.getStartTime().format(tf) + "–" + e.getEndTime().format(tf);
				String duration = durationStr(e.getMinutes());
				String status = "APPROVED";
				String[] row = new String[] { date, weekday, time, duration, status };

				Color bg = (i % 2 == 0) ? Color.WHITE : s.zebra;
				drawTableRowBackground(s, tableX, y, tableW, rowH, bg);
				drawTableRowBorders(s, tableX, y, colW, rowH, false);
				drawTableRowText(s, tableX, y, colW, rowH, row, false);
				y -= rowH;
			}
		}

		private void drawTableRowBackground(PdfStyle s, float x, float topY, float w, float h, Color bg) throws Exception {
			cs.setNonStrokingColor(bg);
			cs.addRect(x, topY - h + 2, w, h);
			cs.fill();
		}

		private void drawTableRowBorders(PdfStyle s, float x, float topY, float[] colW, float h, boolean header) throws Exception {
			cs.setStrokingColor(header ? s.accent : s.line);
			cs.setLineWidth(header ? 1.2f : 1f);
			float y0 = topY - h + 2;
			float w = 0;
			for (float cw : colW) w += cw;
			cs.addRect(x, y0, w, h);
			cs.stroke();

			float cx = x;
			for (int i = 0; i < colW.length - 1; i++) {
				cx += colW[i];
				cs.moveTo(cx, y0);
				cs.lineTo(cx, y0 + h);
			}
			cs.stroke();
		}

		private void drawTableRowText(PdfStyle s, float x, float topY, float[] colW, float h, String[] values, boolean header) throws Exception {
			float yText = topY - 12;
			float cx = x;
			for (int i = 0; i < values.length; i++) {
				String v = values[i] != null ? values[i] : "";
				Color c = header ? s.tableHeaderText : s.text;
				PDType1Font f = header ? s.fontBold : s.fontRegular;
				String txt = header ? v.toUpperCase(Locale.ENGLISH) : v;
				s.drawText(cs, truncate(txt, 22), cx + 8, yText, f, 9.6f, c);
				cx += colW[i];
			}
		}

		private List<String> wrapText(PdfStyle s, String text, PDType1Font font, float fontSize, float maxWidth) throws Exception {
			if (text == null) return List.of("");
			String t = text.trim();
			if (t.isEmpty()) return List.of("");

			// Prefer splitting by ", " for date lists; fallback to whitespace.
			String[] parts = t.contains(",") ? t.split(",\\s*") : t.split("\\s+");
			List<String> lines = new ArrayList<>();
			StringBuilder cur = new StringBuilder();
			for (String part : parts) {
				String token = part == null ? "" : part.trim();
				if (token.isEmpty()) continue;
				String candidate = cur.isEmpty() ? token : (cur + ", " + token);
				if (s.textWidth(candidate, font, fontSize) <= maxWidth) {
					cur.setLength(0);
					cur.append(candidate);
				} else {
					if (!cur.isEmpty()) lines.add(cur.toString());
					// Token itself may be longer than the cell; hard-cut as last resort.
					if (s.textWidth(token, font, fontSize) <= maxWidth) {
						cur.setLength(0);
						cur.append(token);
					} else {
						// Hard wrap token by characters (rare).
						StringBuilder tmp = new StringBuilder();
						for (int i = 0; i < token.length(); i++) {
							tmp.append(token.charAt(i));
							if (s.textWidth(tmp.toString(), font, fontSize) > maxWidth && tmp.length() > 1) {
								tmp.setLength(tmp.length() - 1);
								lines.add(tmp.toString());
								tmp.setLength(0);
								tmp.append(token.charAt(i));
							}
						}
						if (!tmp.isEmpty()) cur.setLength(0);
						if (!tmp.isEmpty()) cur.append(tmp);
					}
				}
			}
			if (!cur.isEmpty()) lines.add(cur.toString());
			return lines.isEmpty() ? List.of("") : lines;
		}

		void drawVacationTable(PdfStyle s, int year, Map<YearMonth, List<LocalDate>> byMonth, DateTimeFormatter fmtDate) throws Exception {
			ensureSpace(s, 60);
			float tableX = s.margin;
			float tableW = s.contentWidth;
			float rowH = 18;
			float[] colW = new float[] { 120, 70, 320 }; // Month, Count, Dates
			String[] headers = new String[] { "Month", "Days", "Dates" };

			ensureSpace(s, 34);
			drawTableRowBackground(s, tableX, y, tableW, rowH, s.tableHeaderBg);
			drawTableRowBorders(s, tableX, y, colW, rowH, true);
			drawTableRowText(s, tableX, y, colW, rowH, headers, true);
			y -= rowH;

			if (byMonth == null || byMonth.isEmpty()) {
				String[] empty = new String[] { String.valueOf(year), "0", "No vacation days selected." };
				drawTableRowBackground(s, tableX, y, tableW, rowH, Color.WHITE);
				drawTableRowBorders(s, tableX, y, colW, rowH, false);
				// No wrapping needed here
				drawTableRowText(s, tableX, y, colW, rowH, empty, false);
				y -= rowH;
				return;
			}

			List<Map.Entry<YearMonth, List<LocalDate>>> rows = new ArrayList<>(byMonth.entrySet());
			for (int i = 0; i < rows.size(); i++) {
				var e = rows.get(i);
				String month = e.getKey().format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH));
				List<LocalDate> dates = e.getValue() != null ? e.getValue().stream().sorted().toList() : List.of();
				String count = String.valueOf(dates.size());
				String datesText = dates.stream().map(d -> d.format(fmtDate)).collect(Collectors.joining(", "));

				// Wrap dates into multiple lines (no truncation).
				float fontSize = 9.2f;
				float cellPaddingX = 8f;
				float maxDatesWidth = colW[2] - (cellPaddingX * 2);
				List<String> dateLines = wrapText(s, datesText, s.fontRegular, fontSize, maxDatesWidth);
				float leading = 11f;
				float dynamicH = Math.max(rowH, 6 + dateLines.size() * leading);

				ensureSpace(s, dynamicH + 10);
				Color bg = (i % 2 == 0) ? Color.WHITE : s.zebra;
				drawTableRowBackground(s, tableX, y, tableW, dynamicH, bg);
				drawTableRowBorders(s, tableX, y, colW, dynamicH, false);

				// Month + count on first line
				float yText = y - 12;
				s.drawText(cs, truncate(month, 22), tableX + 8, yText, s.fontRegular, 9.5f, s.text);
				s.drawText(cs, truncate(count, 22), tableX + colW[0] + 8, yText, s.fontRegular, 9.5f, s.text);

				// Dates wrapped in the third column
				float datesX = tableX + colW[0] + colW[1] + 8;
				float yy = yText;
				for (String line : dateLines) {
					s.drawText(cs, line, datesX, yy, s.fontRegular, fontSize, s.text);
					yy -= leading;
				}

				y -= dynamicH;
			}
		}
	}

	private static String durationStr(int minutes) {
		int h = minutes / 60;
		int m = minutes % 60;
		return m == 0 ? (h + "h") : (h + "h " + m + "m");
	}

	private static String truncate(String s, int max) {
		if (s == null) return "";
		if (s.length() <= max) return s;
		return s.substring(0, Math.max(0, max - 1)) + "…";
	}

	private static String describeVacationStatus(String status) {
		String s = status != null ? status.trim().toUpperCase(Locale.ENGLISH) : "";
		return switch (s) {
			case "DRAFT" -> "Draft (not submitted yet)";
			case "PENDING" -> "Pending approval";
			case "APPROVED" -> "Approved";
			case "REJECTED" -> "Rejected";
			default -> "—";
		};
	}
}

