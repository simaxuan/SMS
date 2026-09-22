// ECharts 按需引入模块（第五轮优化1）
// 仅注册本项目实际用到的图表类型与组件，而非全量引入 echarts，
// 从而显著缩小 vendor-echarts 单包体积、降低首屏加载量。
import * as echarts from 'echarts/core'
// 图表类型
import { BarChart, PieChart, LineChart } from 'echarts/charts'
// 组件：坐标系/提示/图例/标题
import { GridComponent, TooltipComponent, LegendComponent, TitleComponent } from 'echarts/components'
// 渲染器：本项目仅用 Canvas
import { CanvasRenderer } from 'echarts/renderers'

echarts.use([
  BarChart,
  PieChart,
  LineChart,
  GridComponent,
  TooltipComponent,
  LegendComponent,
  TitleComponent,
  CanvasRenderer
])

export default echarts
