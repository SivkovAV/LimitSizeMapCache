/*
 * Copyright (c) 2020 Aleksei Sivkov.
 * All rights reserved.
 */

package stereo.rchain.limitsizemapcache

sealed trait GoogleVisualizationTemplate {
  def html(dataStringWith2DArray: String, description: String): String
}

class LineChartTemplate extends GoogleVisualizationTemplate {
  def html(dataStringWith2DArray: String, description: String): String = {
    s"""
       |  <html>
       |  <head>
       |    <script type="text/javascript" src="https://www.gstatic.com/charts/loader.js"></script>
       |    <script type="text/javascript">
       |      google.charts.load('current', {'packages':['corechart']});
       |      google.charts.setOnLoadCallback(drawChart);
       |      function drawChart() {
       |        var data = google.visualization.arrayToDataTable($dataStringWith2DArray);
       |        var options = {
       |          title: '$description',
       |          curveType: 'function',
       |          legend: { position: 'bottom' }
       |        };
       |        var chart = new google.visualization.LineChart(document.getElementById('curve_chart'));
       |        chart.draw(data, options);
       |      }
       |      window.onload = drawChart;
       |      window.onresize = drawChart;
       |    </script>
       |  </head>
       |  <body>
       |    <div id="curve_chart" style="width: 100%; height: 100%"></div>
       |  </body>
       |</html>
       |""".stripMargin
  }
}

class BarChartTemplate extends GoogleVisualizationTemplate {
  def html(dataStringWith2DArray: String, description: String): String = {
    s"""
       |<html>
       |  <head>
       |    <script type="text/javascript" src="https://www.gstatic.com/charts/loader.js"></script>
       |    <script type="text/javascript">
       |      google.charts.load('current', {'packages':['bar']});
       |      google.charts.setOnLoadCallback(drawChart);
       |      function drawChart() {
       |        var data = google.visualization.arrayToDataTable($dataStringWith2DArray);
       |        var options = {
       |          chart: {
       |            title: '$description',
       |            subtitle: 'work time - nanoseconds',
       |          }
       |        };
       |        var chart = new google.charts.Bar(document.getElementById('columnchart_material'));
       |        chart.draw(data, google.charts.Bar.convertOptions(options));
       |      }
       |      window.onload = drawChart;
       |      window.onresize = drawChart;
       |    </script>
       |  </head>
       |  <body>
       |    <div id="columnchart_material" style="width: 100%; height: 100%;"></div>
       |  </body>
       |</html>
       |""".stripMargin
  }
}
