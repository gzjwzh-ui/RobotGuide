package com.robot.guide.matcher

import com.robot.guide.data.MatchResult
import com.robot.guide.data.QAItem

/**
 * 智能问答匹配引擎
 * 支持：精确匹配、关键词匹配、相似度匹配、包含匹配
 */
class QAMatcher(private val threshold: Int = 60) {

    /**
     * 从问答库中找到与用户问题最匹配的答案
     * @param userQuestion 用户输入的问题
     * @param qaLibrary 问答库列表
     * @return 最佳匹配结果，低于阈值返回null
     */
    fun match(userQuestion: String, qaLibrary: List<QAItem>): MatchResult? {
        if (qaLibrary.isEmpty() || userQuestion.isBlank()) return null

        val normalizedInput = normalize(userQuestion)
        val results = mutableListOf<MatchResult>()

        for (qa in qaLibrary) {
            val qaQuestion = normalize(qa.question)
            val keywords = qa.keywords.map { normalize(it) }.filter { it.isNotBlank() }

            // 1. 精确匹配 - 满分
            if (qaQuestion == normalizedInput) {
                return MatchResult(qa, 100, null)
            }

            // 2. 检查关键词匹配
            for (keyword in keywords) {
                if (keyword.isNotBlank()) {
                    // 输入包含关键词
                    if (normalizedInput.contains(keyword)) {
                        val score = calculateKeywordScore(keyword, normalizedInput)
                        if (score >= threshold) {
                            results.add(MatchResult(qa, score, keyword))
                        }
                    }
                    // 关键词包含输入（用户说了关键词相关）
                    else if (keyword.contains(normalizedInput) && normalizedInput.length >= 2) {
                        val score = (normalize(normalizedInput).length.toFloat() / keyword.length * 80).toInt()
                        if (score >= threshold) {
                            results.add(MatchResult(qa, score, keyword))
                        }
                    }
                }
            }

            // 3. 问题包含匹配
            if (qaQuestion.contains(normalizedInput) && normalizedInput.length >= 2) {
                val score = (normalizedInput.length.toFloat() / qaQuestion.length * 80 + 15).toInt()
                if (score >= threshold) {
                    results.add(MatchResult(qa, score, null))
                }
            } else if (normalizedInput.contains(qaQuestion) && qaQuestion.length >= 2) {
                val score = (qaQuestion.length.toFloat() / normalizedInput.length * 70 + 10).toInt()
                if (score >= threshold) {
                    results.add(MatchResult(qa, score, null))
                }
            }

            // 4. 相似度匹配 - 基于字符重叠度
            val similarity = calculateSimilarity(normalizedInput, qaQuestion)
            if (similarity >= threshold) {
                results.add(MatchResult(qa, similarity, null))
            }
        }

        // 返回最高分结果
        return results.maxByOrNull { it.score }
    }

    /**
     * 关键词匹配得分计算
     */
    private fun calculateKeywordScore(keyword: String, input: String): Int {
        // 关键词越精准（不是单个字），得分越高
        val base = 70
        val keywordBonus = (keyword.length.coerceAtMost(5) * 6)
        val inputBonus = (input.length.coerceAtMost(8) * 2)
        return (base + keywordBonus + inputBonus).coerceAtMost(100)
    }

    /**
     * 基于N-gram的相似度计算
     */
    private fun calculateSimilarity(s1: String, s2: String): Int {
        if (s1.isEmpty() || s2.isEmpty()) return 0
        if (s1.length < 2 || s2.length < 2) return 0

        val biGrams1 = generateBigrams(s1)
        val biGrams2 = generateBigrams(s2)

        val set1 = biGrams1.toSet()
        val set2 = biGrams2.toSet()

        if (set1.isEmpty() || set2.isEmpty()) return 0

        val intersection = set1.intersect(set2).size.toFloat()
        val union = set1.union(set2).size.toFloat()

        val jaccard = intersection / union
        // 加上共同子串的权重
        val commonLength = longestCommonSubstring(s1, s2).length

        val lenRatio = minOf(s1.length, s2.length).toFloat() / maxOf(s1.length, s2.length)

        val finalScore = ((jaccard * 0.5 + commonLength.toFloat() / maxOf(s1.length, s2.length) * 0.3 + lenRatio * 0.2) * 100).toInt()

        return finalScore.coerceIn(0, 100)
    }

    private fun generateBigrams(s: String): List<String> {
        val result = mutableListOf<String>()
        for (i in 0 until s.length - 1) {
            result.add(s.substring(i, i + 2))
        }
        return result
    }

    private fun longestCommonSubstring(s1: String, s2: String): String {
        val maxLen = minOf(s1.length, s2.length)
        for (len in maxLen downTo 2) {
            for (i in 0..s1.length - len) {
                val sub = s1.substring(i, i + len)
                if (s2.contains(sub)) return sub
            }
        }
        return ""
    }

    /**
     * 归一化文本 - 去除标点、转小写、统一空格
     */
    fun normalize(text: String): String {
        return text
            .lowercase()
            .replace(Regex("[\\s]+"), "")    // 去除所有空白
            .replace(Regex("[\uff0c\u3002\uff01\uff1f\u3001\uff1b\uff1a\u201c\u201d\u2018\u2019\uff08\uff09\u3010\u3011\u300a\u300b!,.?;:()\\[\\]{}<>]"), "")
            .trim()
    }
}
