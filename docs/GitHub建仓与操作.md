# GitHub 建仓与操作

## 仓库及当前阶段

公开仓库已创建：[MFJ-123/stray-animal-rescue-system](https://github.com/MFJ-123/stray-animal-rescue-system)。本次初始化内容包括协作方案、追踪清单、模板和两份基线文档，尚无可运行应用源码。原有 A—E 分工继续适用。

组长后续在 Settings 的 Collaborators 入口添加其余四人的真实 GitHub 用户名；成员接受邀请后即可提交功能分支。当前未发送邀请，也未自动建立 Issue 或配置分支保护。

## 成员获取项目

安装 Git 后，在准备存放项目的目录执行：

```powershell
git clone https://github.com/MFJ-123/stray-animal-rescue-system.git
Set-Location stray-animal-rescue-system
git config user.name '你的提交姓名'
git config user.email '你的提交邮箱'
python scripts/check_plan.py
```

姓名和邮箱替换为自己的提交身份，邮箱可使用 GitHub 提供的隐私邮箱。最后一条命令只检查协作资料，不会启动应用。身份验证在 GitHub/Git 凭据窗口完成，不把密码或令牌写入项目文件。

如果使用 ZIP 获取资料，压缩包不含 .git；团队开发建议使用 git clone，以保留共同历史。不熟悉命令行也可用 GitHub Desktop 克隆现有仓库，不必再次创建同名仓库。

GitHub 设置参见官方[分支保护](https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/managing-protected-branches/about-protected-branches)和[CODEOWNERS](https://docs.github.com/en/repositories/managing-your-repositorys-settings-and-features/customizing-your-repository/about-code-owners)说明。模板不会自动启用这些规则。

## 成员每日操作

开始新任务前确保工作区已提交或妥善保存，再执行：

```powershell
git switch main
git pull --ff-only
git switch -c feat/0014-task-claim
```

完成一块可验收工作后，查看差异，仅添加本任务文件并提交；推送自己的分支，在 GitHub 上新建目标为 main 的 Pull Request。使用 PR 模板说明需求、测试、接口和数据库影响。分支示例仅供参考，不让所有成员使用同一分支名。

功能分支落后主分支时先提交当前工作，再获取远端并合入最新 main：

```powershell
git fetch origin
git merge origin/main
```

解决冲突后重新测试、提交并推送，不强制推送共享分支。PR 合并后切回 main 更新，从新基线开启下一项任务。GitHub Issues 使用模板，任务清单 `docs/backlog.csv` 是待建立 Issue 的清单，不会因上传 CSV 自动生成 Issue。

## 需要补填的事项

- 将 docs/team.csv 的 B—E 映射为真实姓名和 GitHub 用户名。
- `.github/CODEOWNERS.example` 只是映射样例。填入有仓库写权限的真实用户名后，按实际套餐决定是否改为 CODEOWNERS；不能原样启用占位符。
- 协作资料检查工作流上传后才会在 GitHub 运行；本地验证不代表云端已运行。
- G1 完成时，A 补齐应用构建和测试工作流，并把通过的检查列为合并条件。
