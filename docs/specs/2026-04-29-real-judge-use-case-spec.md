# 用例规约：真实判题

<table>
  <tr>
    <td><strong>用例编号</strong></td>
    <td colspan="3">UC-JUDGE-001</td>
  </tr>
  <tr>
    <td><strong>用例名称</strong></td>
    <td colspan="3">真实判题</td>
  </tr>
  <tr>
    <td><strong>用例描述</strong></td>
    <td colspan="3">学生在题目页提交 Python 代码后，系统调用 Judge Server 执行真实测试用例，返回 AC / WA / CE / TLE 判题结果，写入提交记录，并根据结果触发错误诊断或通过复盘。</td>
  </tr>
  <tr>
    <td><strong>范围</strong></td>
    <td colspan="3">Alethicode OJ + AI 教学平台 / 基础 OJ</td>
  </tr>
  <tr>
    <td><strong>主参与者</strong></td>
    <td colspan="3">学生</td>
  </tr>
  <tr>
    <td><strong>次要参与者</strong></td>
    <td colspan="3">Judge Server、OJ 后端提交服务、AI 导学服务、学情投影服务</td>
  </tr>
  <tr>
    <td rowspan="6"><strong>项目相关人利益说明</strong></td>
    <td><strong>项目相关人</strong></td>
    <td colspan="2"><strong>利益</strong></td>
  </tr>
  <tr>
    <td>学生</td>
    <td colspan="2">获得真实、及时、明确的判题结果，知道代码是否通过以及失败类型。</td>
  </tr>
  <tr>
    <td>教师 / 助教</td>
    <td colspan="2">获得学生真实提交记录、错误状态和学习进度，用于教学跟进。</td>
  </tr>
  <tr>
    <td>AI 导学角色</td>
    <td colspan="2">基于 AC / WA / CE / TLE 结果触发对应的错误诊断、通过复盘或迁移练习。</td>
  </tr>
  <tr>
    <td>平台管理员</td>
    <td colspan="2">确保判题资源可控、提交记录可追踪、异常链路可定位。</td>
  </tr>
  <tr>
    <td>Alethicode 平台</td>
    <td colspan="2">保证 OJ 编程闭环可信，形成后续学情分析和自适应推荐的数据基础。</td>
  </tr>
  <tr>
    <td><strong>前置条件</strong></td>
    <td colspan="3">
      1. 学生已登录 Alethicode 平台。<br>
      2. 学生已进入可访问的题目详情页。<br>
      3. 题目已发布，且测试用例配置完整。<br>
      4. 平台支持学生当前选择的编程语言；本规约默认语言为 Python3。<br>
      5. Judge Server 已注册并处于可用状态。<br>
      6. 学生待提交代码不为空。
    </td>
  </tr>
  <tr>
    <td><strong>后置条件</strong></td>
    <td colspan="3">
      <strong>成功：</strong>系统生成一条提交记录，保存代码、语言、判题状态、耗时、内存和错误信息；前端展示最终判题结果；若结果为 AC，系统更新题目通过状态和 KC 掌握度，并可触发通过复盘。<br>
      <strong>失败：</strong>系统不将本次提交标记为 AC；已进入判题流程的失败结果按 WA / CE / TLE 写入提交记录；若判题流程未能开始，系统直接返回提交失败原因。
    </td>
  </tr>
  <tr>
    <td><strong>成功保证</strong></td>
    <td colspan="3">
      1. 每次有效提交都有唯一提交编号。<br>
      2. 最终判题状态可在提交历史中查询。<br>
      3. 判题结果与提交代码、题目、学生账号保持一致绑定。<br>
      4. 非 AC 结果不会更新题目通过状态。<br>
      5. 错误诊断和通过复盘只基于最终判题状态触发。
    </td>
  </tr>
  <tr>
    <td rowspan="13"><strong>基本事件流</strong></td>
    <td>1</td>
    <td colspan="2">学生进入题目详情页，打开代码编辑器。</td>
  </tr>
  <tr>
    <td>2</td>
    <td colspan="2">学生选择 Python3 语言，并编写待提交代码。</td>
  </tr>
  <tr>
    <td>3</td>
    <td colspan="2">学生点击“提交”。</td>
  </tr>
  <tr>
    <td>4</td>
    <td colspan="2">前端向 OJ 后端提交 problem_id、language、code。</td>
  </tr>
  <tr>
    <td>5</td>
    <td colspan="2">OJ 后端校验登录态、题目可访问性、语言支持状态和代码内容。</td>
  </tr>
  <tr>
    <td>6</td>
    <td colspan="2">OJ 后端创建提交记录，初始状态为“等待判题”。</td>
  </tr>
  <tr>
    <td>7</td>
    <td colspan="2">OJ 后端将提交任务分配给可用 Judge Server。</td>
  </tr>
  <tr>
    <td>8</td>
    <td colspan="2">Judge Server 加载题目测试用例，在沙箱环境中运行学生代码。</td>
  </tr>
  <tr>
    <td>9</td>
    <td colspan="2">Judge Server 汇总测试点执行结果，生成 AC / WA / CE / TLE 判题状态。</td>
  </tr>
  <tr>
    <td>10</td>
    <td colspan="2">OJ 后端接收判题结果，更新提交记录的状态、耗时、内存和错误信息。</td>
  </tr>
  <tr>
    <td>11</td>
    <td colspan="2">前端刷新提交状态，并在题目页展示最终判题结果。</td>
  </tr>
  <tr>
    <td>12</td>
    <td colspan="2">若结果为 AC，系统更新学生题目通过状态和 KC 掌握度，并触发“通过复盘”扩展用例。</td>
  </tr>
  <tr>
    <td>13</td>
    <td colspan="2">用例结束。</td>
  </tr>
  <tr>
    <td rowspan="9"><strong>扩展事件流</strong></td>
    <td>4a</td>
    <td colspan="2">提交参数缺失：步骤 4 中若 problem_id、language 或 code 缺失，系统拒绝提交并提示缺少必要字段；学生返回步骤 2 补全后重新提交。</td>
  </tr>
  <tr>
    <td>5a</td>
    <td colspan="2">学生未登录或登录态失效：步骤 5 中若登录态无效，系统拒绝提交并提示重新登录；用例结束。</td>
  </tr>
  <tr>
    <td>5b</td>
    <td colspan="2">题目不可访问：步骤 5 中若题目不存在、未发布或学生无权访问，系统拒绝提交并提示题目不可访问；用例结束。</td>
  </tr>
  <tr>
    <td>5c</td>
    <td colspan="2">语言不支持：步骤 5 中若语言不支持，系统拒绝提交并提示当前语言不支持；学生返回步骤 2 重新选择语言。</td>
  </tr>
  <tr>
    <td>7a</td>
    <td colspan="2">无可用 Judge Server：步骤 7 中若没有可用判题服务，系统拒绝进入判题流程并提示判题服务暂不可用；用例结束。</td>
  </tr>
  <tr>
    <td>8a</td>
    <td colspan="2">编译失败：步骤 8 中若代码编译失败，系统写入 CE 判题结果，前端展示编译错误摘要，并触发“错误诊断”扩展用例；用例结束。</td>
  </tr>
  <tr>
    <td>8b</td>
    <td colspan="2">运行超时：步骤 8 中若代码运行超过时间限制，系统写入 TLE 判题结果，前端展示超时信息，并触发“错误诊断”扩展用例；用例结束。</td>
  </tr>
  <tr>
    <td>9a</td>
    <td colspan="2">答案错误：步骤 9 中若存在测试点答案错误，系统写入 WA 判题结果，前端展示答案错误状态，并触发“错误诊断”扩展用例；用例结束。</td>
  </tr>
  <tr>
    <td>10a</td>
    <td colspan="2">判题结果回传数据不完整：步骤 10 中若 Judge Server 回传数据不完整，系统 fail-fast 拒绝写入不完整结果，记录内部错误；用例结束。</td>
  </tr>
  <tr>
    <td rowspan="4"><strong>子事件流</strong></td>
    <td>1</td>
    <td colspan="2">查询判题状态：前端根据提交编号查询或订阅判题状态，直到状态进入 AC / WA / CE / TLE 之一。</td>
  </tr>
  <tr>
    <td>2</td>
    <td colspan="2">触发错误诊断：当最终状态为 WA / CE / TLE 时，AI 导学服务读取题目、代码、错误摘要和最近学习上下文，生成错误诊断卡片。</td>
  </tr>
  <tr>
    <td>3</td>
    <td colspan="2">触发通过复盘：当最终状态为 AC 时，AI 导学服务读取通过代码、题目 KC 和提交表现，生成通过复盘卡片。</td>
  </tr>
  <tr>
    <td>4</td>
    <td colspan="2">学情同步：系统将提交结果投影到学生题目进度、KC 掌握度和教师端学情数据。</td>
  </tr>
  <tr>
    <td><strong>规则与约束</strong></td>
    <td colspan="3">
      业务规则：只有最终状态为 AC 的提交可以更新题目通过状态。<br>
      业务规则：WA / CE / TLE 只能记录为失败提交，不得被视为部分通过。<br>
      判题规则：判题结果必须来自真实测试用例执行，不允许使用 Quiz 结果替代。<br>
      判题规则：测试用例明文和标准答案不得返回给学生端。<br>
      数据规则：提交记录必须绑定学生、题目、语言、代码快照和最终判题状态。<br>
      设计约束：非法输入直接 fail-fast，不做静默纠偏。<br>
      设计约束：错误诊断、通过复盘属于扩展用例，不改变真实判题本身的最终状态。
    </td>
  </tr>
</table>

