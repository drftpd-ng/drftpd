/*
 * Created on 2004-apr-22
 *
 * To change the template for this generated file go to
 * Window - Preferences - Java - Code Generation - Code and Comments
 */
package org.drftpd.friendly;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GridLayout;
import java.awt.geom.Ellipse2D;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.text.DateFormat;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Locale;
import java.util.StringTokenizer;

import javax.swing.JPanel;

import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Logger;
import org.drftpd.plugins.XferLog;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.labels.StandardXYItemLabelGenerator;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.StackedXYAreaRenderer;
import org.jfree.chart.renderer.StandardXYItemRenderer;
import org.jfree.chart.renderer.XYAreaRenderer;
import org.jfree.data.SeriesException;
import org.jfree.data.TableXYDataset;
import org.jfree.data.time.Hour;
import org.jfree.data.time.RegularTimePeriod;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;
import org.jfree.data.time.TimeTableXYDataset;
import org.jfree.ui.ApplicationFrame;
import org.jfree.ui.RefineryUtilities;

/**
 * @author mog
 */
public class XferLogGraph extends ApplicationFrame {
	private static final Logger logger = Logger.getLogger(XferLogGraph.class);
//	public class Mogmog {
//		Hashtable _users = new Hashtable();
//		private Object _defaultValue;
//		public Mogmog(Object defaultValue) {
//			_defaultValue = defaultValue;
//		}
//
//		public Object get(Object key) {
//			Object ret = _users.get(key);
//			if(ret == null) {
//				ret = ((Cloneable)_defaultValue).clone();
//				
//			}
//			return ret;
//		}
//		public void put(Object key, Object val) {
//			_users.put(key, val);
//		}
//	}
	public XferLogGraph() throws IOException {
		super("XferLog");
		boolean showUsers = true;
		//int skip=1;
		
		Class timePeriodClass = Hour.class;
		TimeSeries tsUp = new TimeSeries("Up", timePeriodClass);
		TimeSeries tsDown = new TimeSeries("Down", timePeriodClass);
		BufferedReader in = new BufferedReader(new FileReader("/home/mog/xferlog-apr"));

		Hashtable users = null;
		if(showUsers) {users = new Hashtable(); }
		String line;
		RegularTimePeriod timePeriod = null;
		long endOfPeriod = 0;

		while((line = in.readLine()) != null) {
			ParsePosition pos = new ParsePosition(0);
			Date date = XferLog.DATE_FMT.parse(line, pos);
			if(timePeriod == null || date.getTime() > endOfPeriod) {
				{
					try {
						timePeriod = (RegularTimePeriod) timePeriodClass.getConstructor(new Class[] { Date.class }).newInstance(new Object[] {date});
					} catch (Exception e) {
						throw new RuntimeException(e);
					}
				}
				endOfPeriod = timePeriod.getLastMillisecond();
			}
			Calendar cal = Calendar.getInstance();
			cal.setTime(date);
			StringTokenizer st = new StringTokenizer(line.substring(pos.getIndex()));
			int transferTimeSeconds = Integer.parseInt(st.nextToken());
			//String remoteHost = 
			st.nextToken();
			long fileSize = Long.parseLong(st.nextToken());
			//String path = 
			st.nextToken(); 
			//String transferType =
			st.nextToken();
			st.nextToken(); // special-action-flag
			
			String tok = st.nextToken();
			boolean isIncoming = tok.equals("i");
			if(!isIncoming && !tok.equals("o")) throw new IOException("Expected i or o, not '"+tok+"'");
			
			st.nextToken(); // access-mode
			
			String userName = st.nextToken(); // user-name
			
			//st.nextToken(); // service-name

			TimeSeries tsUser = null;
			if(users != null) {
			tsUser = (TimeSeries) users.get(userName);
			if(tsUser == null) {
				tsUser = new TimeSeries(userName, timePeriodClass);
				//tsUser = new TimeTableXYDataset()
				users.put(userName, tsUser);
			}
			}

			//use transferTime
			
			int periodLengthSeconds = (int) ((timePeriod.next().getFirstMillisecond()-timePeriod.getFirstMillisecond())/1000);
			
			long bytesPerPeriod = transferTimeSeconds == 0 ? fileSize : fileSize/transferTimeSeconds;
			Long bytesPerPeriodO = new Long(Math.min(bytesPerPeriod, 2000000));

			TimeSeries ts = isIncoming ? tsUp : tsDown;

			//tsUser.addOrUpdate(timePeriod, bytesPerSecondO);
			//ts.addOrUpdate(timePeriod, bytesPerSecondO);
			
			RegularTimePeriod tmpPeriod = timePeriod.next();
			if(tsUser != null) tsUser.addOrUpdate(tmpPeriod, new Integer(0));
			ts.addOrUpdate(tmpPeriod, new Integer(0));

			tmpPeriod = timePeriod;
			for(int i=transferTimeSeconds; i > 0; i-=periodLengthSeconds) {
				ts.addOrUpdate(tmpPeriod, bytesPerPeriodO);
				if(tsUser != null) tsUser.addOrUpdate(tmpPeriod, bytesPerPeriodO);
				//logger.debug("Updated "+tmpPeriod);
				tmpPeriod = tmpPeriod.previous();
			}
			try {
				ts.add(tmpPeriod, 0);
			} catch (SeriesException e) {
			}
			try {
				if(tsUser != null) tsUser.add(tmpPeriod, 0);
			} catch (SeriesException e) {
			}
		}

		TimeSeriesCollection dataset = new TimeSeriesCollection();
		dataset.addSeries(tsUp);
		dataset.addSeries(tsDown);
		dataset.setDomainIsPointsInTime(false);
		
		// UP & DOWN
		JPanel panel = new JPanel(new GridLayout(2, 1));
		JFreeChart chart = ChartFactory.createTimeSeriesChart(
	            "Up&Down",
	            "Time", 
	            "Bytes/Sec",
	            dataset,
	            true,
	            true,
	            false
	        );

        ChartPanel chartPanel = new ChartPanel(chart);
        chartPanel.setPreferredSize(new java.awt.Dimension(500, 270));
        panel.add(chartPanel);

        
        if(users != null) {
        	dataset = new TimeSeriesCollection();
        	//dataset = new TimeTableXYDataset();
			for (Iterator iter = users.values().iterator(); iter.hasNext();) {
				TimeSeries tmpTs = (TimeSeries) iter.next();
				dataset.addSeries(tmpTs);
			}

//		chart = ChartFactory.createStackedAreaChart(
//	            "Users",
//	            "Time",
//	            "Bytes/Sec",
//	            dataset,
//				PlotOrientation.VERTICAL,
//	            true,
//	            true,
//	            false
//	        );
        StandardXYItemLabelGenerator labelGenerator = new StandardXYItemLabelGenerator(
                DateFormat.getInstance()
            );
            DateAxis timeAxis = new DateAxis("Time");
            //xAxis.setLowerMargin(0.0);
            //xAxis.setUpperMargin(0.0);

            NumberAxis valueAxis = new NumberAxis("Bytes/Sec");
            valueAxis.setAutoRangeIncludesZero(false);
            StackedXYAreaRenderer renderer = new StackedXYAreaRenderer(
                XYAreaRenderer.AREA_AND_SHAPES, labelGenerator, null
            );
//            renderer.setOutline(true);
//            renderer.setSeriesPaint(0, new Color(255, 255, 206));
//            renderer.setSeriesPaint(1, new Color(206, 230, 255));
//            renderer.setSeriesPaint(2, new Color(255, 230, 230));
//            renderer.setShapePaint(Color.gray);
//            renderer.setShapeStroke(new BasicStroke(0.5f));
//            renderer.setShape(new Ellipse2D.Double(-3, -3, 6, 6));

            XYPlot plot = new XYPlot(dataset, timeAxis, valueAxis, null);
            plot.setRenderer(new StackedXYAreaRenderer(XYAreaRenderer.AREA, null, null));
            chart = new JFreeChart(null, JFreeChart.DEFAULT_TITLE_FONT, plot, true);

        chartPanel = new ChartPanel(chart);
        chartPanel.setPreferredSize(new java.awt.Dimension(500, 270));
        panel.add(chartPanel);
		}
        setContentPane(panel);

	}
	
	public static void main(String args[]) throws IOException {
		//new XferLogGraph();
        BasicConfigurator.configure();
        XferLogGraph demo = new XferLogGraph();
        demo.pack();
        RefineryUtilities.centerFrameOnScreen(demo);
        demo.setVisible(true);

	}
}
