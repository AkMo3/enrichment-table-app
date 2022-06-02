package org.nrnb.gsoc.enrichment.tasks;

import org.cytoscape.model.CyTable;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PiePlot;
import org.jfree.chart.title.TextTitle;
import org.jfree.chart.ui.HorizontalAlignment;
import org.jfree.data.general.DefaultPieDataset;
import org.jfree.data.general.PieDataset;

import javax.swing.*;
import java.awt.*;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;


public class EnrichmentChartVisualisationTask extends JFrame {
    public final JFreeChart enrichmentChart;

    public EnrichmentChartVisualisationTask(CyTable enrichmentTable) {
        super("Enrichment Pie Chart");
        HashMap<String, Integer> enrichmentResults = new HashMap<>();
        List<String> name = enrichmentTable.getColumn("term id")
                .getValues(String.class);
        List<Integer> intersection = enrichmentTable.getColumn("intersection size")
                .getValues(Integer.class);

        for (int i = 0; i < name.size(); i++) enrichmentResults.put(name.get(i), intersection.get(i));
        enrichmentResults = sortByValue(enrichmentResults);
        final PieDataset<String> dataset = createDataset(enrichmentResults);
        this.enrichmentChart = createChart(dataset);
        final ChartPanel chartPanel = new ChartPanel(enrichmentChart, false);
        chartPanel.setPreferredSize(new Dimension(500, 270));
        setContentPane(chartPanel);
    }

    private static PieDataset<String> createDataset(Map<String, Integer> stringIntegerMap) {
        DefaultPieDataset<String> dataset = new DefaultPieDataset<String>();
        stringIntegerMap.forEach(dataset::setValue);
        System.out.println("Dataset item count: " + dataset.getItemCount());
        return dataset;
    }

    private static JFreeChart createChart(PieDataset<String> dataset) {
        DefaultPieDataset<String> limitedDataset = new DefaultPieDataset<String>();
        int maxElements = Math.min(20, dataset.getItemCount());
        while(maxElements-- > 0) limitedDataset.setValue(dataset.getKey(maxElements), dataset.getValue(maxElements));
        dataset = limitedDataset;
        JFreeChart chart = ChartFactory.createPieChart("Enrichment Analysis Result", dataset, false,
                true, false);
        chart.setBackgroundPaint(new GradientPaint(new Point(0, 0),
                new Color(20, 20, 20), new Point(400, 200), Color.DARK_GRAY));

        // customise the title position and font
        TextTitle t = chart.getTitle();
        t.setHorizontalAlignment(HorizontalAlignment.LEFT);
        t.setPaint(new Color(240, 240, 240));
        t.setFont(new Font("Arial", Font.BOLD, 26));

        PiePlot plot = (PiePlot) chart.getPlot();
        plot.setBackgroundPaint(null);
        plot.setInteriorGap(0.04);
        plot.setOutlineVisible(false);

        // use gradients and white borders for the section colours
        dataset.getKeys().forEach(e -> plot.setSectionPaint(e,
                createColor()));
        plot.setDefaultSectionOutlinePaint(Color.WHITE);
        plot.setSectionOutlinesVisible(true);
        plot.setDefaultSectionOutlineStroke(new BasicStroke(2.0f));

        // customise the section label appearance
        plot.setLabelFont(new Font("Courier New", Font.BOLD, 20));
        plot.setLabelLinkPaint(Color.WHITE);
        plot.setLabelLinkStroke(new BasicStroke(2.0f));
        plot.setLabelOutlineStroke(null);
        plot.setLabelPaint(Color.WHITE);
        plot.setLabelBackgroundPaint(null);

        return chart;
    }

    private static Color createColor() {
        float hue = (float) Math.random();
        return Color.getHSBColor(hue,0.5f,0.5f);
    }

    public static HashMap<String, Integer> sortByValue(HashMap<String, Integer> hm)
    {
        List<Map.Entry<String, Integer> > list =
                new LinkedList<>(hm.entrySet());

        list.sort(Map.Entry.comparingByValue());

        HashMap<String, Integer> temp = new LinkedHashMap<String, Integer>();
        for (Map.Entry<String, Integer> aa : list) {
            temp.put(aa.getKey(), aa.getValue());
        }
        return temp;
    }
}
