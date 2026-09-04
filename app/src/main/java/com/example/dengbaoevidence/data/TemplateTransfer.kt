package com.example.dengbaoevidence.data

import org.json.JSONArray
import org.json.JSONObject

private const val TRANSFER_OTHER_ITEM_NAME = "其他"

enum class TemplateConflictStrategy {
  OVERWRITE,
  RENAME,
  SKIP,
}

data class TemplateTransferPackage(
  val templates: List<TransferredTemplate>,
)

data class TransferredTemplate(
  val name: String,
  val items: List<TransferredTemplateItem>,
)

data class TransferredTemplateItem(
  val name: String,
  val displayOrder: Int,
  val isKeyScreenshot: Boolean,
  val wordOrder: Int?,
  val excludedFromProgress: Boolean,
)

data class TemplateImportPreview(
  val templateCount: Int,
  val duplicateNames: List<String>,
)

data class TemplateImportResult(
  val importedCount: Int,
  val overwrittenCount: Int,
  val renamedCount: Int,
  val skippedCount: Int,
)

object TemplateTransfer {
  const val FORMAT_VERSION = 1

  fun encode(templates: List<DeviceTypeWithItems>, exportedAt: Long = System.currentTimeMillis()): String =
    JSONObject()
      .put("formatVersion", FORMAT_VERSION)
      .put("exportedAt", exportedAt)
      .put(
        "templates",
        JSONArray().apply {
          templates.forEachIndexed { templateIndex, template ->
            put(
              JSONObject()
                .put("name", template.template.name)
                .put("sortOrder", templateIndex)
                .put(
                  "items",
                  JSONArray().apply {
                    template.items.sortedBy { it.displayOrder }.forEach { item ->
                      put(
                        JSONObject()
                          .put("name", item.name)
                          .put("displayOrder", item.displayOrder)
                          .put("isKeyScreenshot", item.isKeyScreenshot)
                          .put("wordOrder", item.wordOrder ?: JSONObject.NULL)
                          .put("excludedFromProgress", item.excludedFromProgress),
                      )
                    }
                  },
                ),
            )
          }
        },
      )
      .toString(2)

  fun decode(text: String): TemplateTransferPackage {
    val root = JSONObject(text)
    require(root.optInt("formatVersion", -1) == FORMAT_VERSION) { "不支持的模板文件版本" }
    val templatesJson = root.optJSONArray("templates") ?: error("模板文件缺少 templates 数据")
    require(templatesJson.length() > 0) { "模板文件中没有设备类型" }
    val templates = buildList {
      repeat(templatesJson.length()) { templateIndex ->
        val templateJson = templatesJson.getJSONObject(templateIndex)
        val name = templateJson.optString("name").trim()
        require(name.isNotEmpty()) { "第 ${templateIndex + 1} 个设备类型名称为空" }
        val itemsJson = templateJson.optJSONArray("items") ?: JSONArray()
        val parsedItems = buildList {
          repeat(itemsJson.length()) { itemIndex ->
            val itemJson = itemsJson.getJSONObject(itemIndex)
            val itemName = itemJson.optString("name").trim()
            require(itemName.isNotEmpty()) { "“$name”的第 ${itemIndex + 1} 个取证项名称为空" }
            val displayOrder = itemJson.optInt("displayOrder", itemIndex)
            require(displayOrder >= 0) { "“$name / $itemName”的显示顺序无效" }
            val wordOrder = if (itemJson.isNull("wordOrder") || !itemJson.has("wordOrder")) null else itemJson.getInt("wordOrder")
            require(wordOrder == null || wordOrder >= 0) { "“$name / $itemName”的 Word 顺序无效" }
            add(
              TransferredTemplateItem(
                name = itemName,
                displayOrder = displayOrder,
                isKeyScreenshot = itemJson.optBoolean("isKeyScreenshot", false),
                wordOrder = wordOrder,
                excludedFromProgress = itemJson.optBoolean("excludedFromProgress", itemName == TRANSFER_OTHER_ITEM_NAME),
              ),
            )
          }
        }
        require(parsedItems.map { it.name }.distinct().size == parsedItems.size) { "“$name”中存在同名取证项" }
        val orderedItems = parsedItems.sortedBy { it.displayOrder }.filterNot { it.name == TRANSFER_OTHER_ITEM_NAME }
          .mapIndexed { index, item -> item.copy(displayOrder = index, excludedFromProgress = false) } +
          TransferredTemplateItem(
            name = TRANSFER_OTHER_ITEM_NAME,
            displayOrder = parsedItems.count { it.name != TRANSFER_OTHER_ITEM_NAME },
            isKeyScreenshot = false,
            wordOrder = null,
            excludedFromProgress = true,
          )
        add(TransferredTemplate(name = name, items = orderedItems))
      }
    }
    require(templates.map { it.name }.distinct().size == templates.size) { "模板文件中存在同名设备类型" }
    return TemplateTransferPackage(templates)
  }
}
