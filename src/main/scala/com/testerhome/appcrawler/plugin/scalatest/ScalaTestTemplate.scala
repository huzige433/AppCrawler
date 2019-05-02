package com.testerhome.appcrawler.plugin.scalatest

import com.testerhome.appcrawler.data.AbstractElementStore
import com.testerhome.appcrawler.data.AbstractElementStore.Status
import com.testerhome.appcrawler._
import org.scalatest
import org.scalatest._

import scala.collection.JavaConversions._
import scala.reflect.io.File

/**
  * Created by seveniruby on 2017/3/25.
  */
class ScalaTestTemplate extends FunSuite with BeforeAndAfterAllConfigMap with Matchers with CommonLog {
  var name = "template"
  var uri = ""

  override def suiteName = name

  def addTestCase() {
    log.trace(s"Report.store.elementStore size = ${ReportFactory.store.storeMap.size}")
    log.trace(s"uri=${uri}")
    val sortedElements = ReportFactory.store.storeMap
      .filter(x => x._2.getElement.getUrl == uri)
      .map(_._2).toList
      .sortBy(_.getClickedIndex)

    log.trace(s"sortedElements=${sortedElements.size}")
    val selected = if (ReportFactory.showCancel) {
      log.info("show all elements")
      //把未遍历的放到后面
      sortedElements.filter(_.getAction == Status.CLICKED) ++
        //sortedElements.filter(_.action == ElementStatus.Skipped) ++
        sortedElements.filter(_.getAction == Status.READY)
    } else {
      log.info("only show clicked elements")
      sortedElements.filter(_.getAction == Status.CLICKED)
    }
    log.trace(s"selected elements size = ${selected.size}")
    selected.foreach(ele => {
      val testcase = ele.getElement.getXpath.replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "")
        .replace("\r", "")

      log.debug(s"add testcase ${testcase}")
      //todo: 增加ignore和cancel的区分
      test(s"clickedIndex=${ele.getClickedIndex} action=${ele.getAction}\nxpath=${testcase}") {
        ele.getAction match {
          case Status.CLICKED => {
              markup(
                s"""
                   |
               |<img src='${File(ele.getReqImg).name}' width='80%' />
                   |<br></br>
                   |<p>after clicked</p>
                   |<img src='${File(ele.getResImg).name}' width='80%' />
          """.stripMargin
              )

              /*
              markup(
                s"""
                |
                |<pre>
                |<xmp>
                |${ele.reqDom.replaceFirst("xml", "x_m_l")}
                |</xmp>
                |</pre>
              """.stripMargin
              )
              */

              AppCrawler.crawler.conf.assertGlobal.foreach(step => {
                if (
                  step.getGiven().forall(g=>XPathUtil.getNodeListByKey(g, ele.getReqDom).size>0)
                ) {
                  log.info(s"match testcase ${ele.getElement.getXpath}")

                  if(step.then!=null) {
                    val cp = new scalatest.Checkpoints.Checkpoint
                    step.then.foreach(existAssert => {
                      log.debug(existAssert)
                      cp {
                        withClue(s"${existAssert} 不存在\n") {
                          XPathUtil.getNodeListByXPath(existAssert, ele.getResDom).size should be > 0
                        }
                      }
                    })
                    cp.reportAll()
                  }
                } else {

/*                  XPathUtil.getNodeListFromXPath(step.getXPath(), ele.reqDom)
                    .map(_.getOrElse("xpath", "")).foreach(log.warn)
                  log.warn(s"not match ${step.getXPath()} ${ele.element.xpath}")*/
                }
              })

          }
          case Status.READY => {
            cancel(s"${ele.getAction}  not click")
          }
          case Status.SKIPPED => {
            cancel(s"${ele.getAction}  skipped")
          }
        }

      }
    })
  }
}

object ScalaTestTemplate extends CommonLog {
  def saveTestCase(store: AbstractElementStore, resultDir: String): Unit = {
    log.info("save testcase")
    ReportFactory.reportPath = resultDir
    ReportFactory.testcaseDir = ReportFactory.reportPath + "/tmp/"
    //为了保持独立使用
    val path = new java.io.File(resultDir).getCanonicalPath

    val suites = store.storeMap.map(x => x._2.getElement.getUrl).toList.distinct
    suites.foreach(suite => {
      log.info(s"gen testcase class ${suite}")
      //todo: 基于规则的多次点击事件只会被保存到一个状态中. 需要区分
      SuiteToClass.genTestCaseClass(
        //todo: Illegal class name  Ⅱ[@]][()
        suite,
        "com.testerhome.appcrawler.plugin.scalatest.TemplateTestCase",
        Map("uri" -> suite, "name" -> suite),
        ReportFactory.testcaseDir
      )
    })
  }

}