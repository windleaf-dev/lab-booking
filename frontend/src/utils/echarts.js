/**
 * 按需引入 ECharts，避免把完整 echarts 打进产物。
 * 目前用到的图表：折线、环形饼图、横向柱状图。
 */
import * as echarts from 'echarts/core'
import { BarChart, LineChart, PieChart } from 'echarts/charts'
import {
  GraphicComponent,
  GridComponent,
  LegendComponent,
  TooltipComponent,
} from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'

echarts.use([
  LineChart,
  PieChart,
  BarChart,
  GridComponent,
  TooltipComponent,
  LegendComponent,
  GraphicComponent,
  CanvasRenderer,
])

export default echarts
