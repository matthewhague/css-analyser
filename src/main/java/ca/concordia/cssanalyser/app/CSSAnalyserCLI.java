package ca.concordia.cssanalyser.app;


import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.slf4j.Logger;

import ca.concordia.cssanalyser.analyser.CSSAnalyser;
import ca.concordia.cssanalyser.crawler.Crawler;
import ca.concordia.cssanalyser.cssmodel.StyleSheet;
import ca.concordia.cssanalyser.io.IOHelper;
import ca.concordia.cssanalyser.migration.topreprocessors.less.LessMigrationOpportunitiesDetector;
import ca.concordia.cssanalyser.migration.topreprocessors.less.LessMixinMigrationOpportunity;
import ca.concordia.cssanalyser.migration.topreprocessors.less.LessPrinter;
import ca.concordia.cssanalyser.parser.CSSParser;
import ca.concordia.cssanalyser.parser.CSSParserFactory;
import ca.concordia.cssanalyser.parser.CSSParserFactory.CSSParserType;
import ca.concordia.cssanalyser.parser.ParseException;
import ca.concordia.cssanalyser.preprocessors.empiricalstudy.EmpiricalStudy;
import ca.concordia.cssanalyser.preprocessors.util.less.ImportInliner;

public class CSSAnalyserCLI {

	public static Logger LOGGER = FileLogger.getLogger(CSSAnalyserCLI.class);

	public static void main(String[] args) throws IOException {

		ParametersParser params = new ParametersParser(args);

		switch (params.getProgramMode()) {
		case CRAWL: {
			if (params.getOutputFolderPath() == null) {
				LOGGER.error("Please provide an output folder using --outfolder:out/folder.");
				return;
			} else if (params.getUrl() == null && params.getListOfURLsToAnalyzeFilePath() == null) {
				LOGGER.error("Please provide a url using --url:http://url/to/site or the file containing list of urls using --urlfile:path/to/url");
				return;
			}

			List<String> urls = new ArrayList<>();

			if (params.getListOfURLsToAnalyzeFilePath() != null) {
				urls.addAll(params.getURLs());
			} else {
				urls.add(params.getUrl());
			}

			for (String currentUrl : urls) {

				String outputFolderPath = params.getOutputFolderPath() + currentUrl.replaceFirst("http[s]?://", "").replaceFirst("file://", "").replace("/", "_").replace(":", "_") + "/";
				// Make sure to configure ca.concordia.cssanalyser.crawler in Crawler class
				Crawler crawler = new Crawler(currentUrl, outputFolderPath);
				crawler.start();

				// Get all ca.concordia.cssanalyser.dom states in outputFolder/crawljax/doms		
				List<File> allStatesFiles = IOHelper.searchForFiles(outputFolderPath + "crawljax/doms", "html");	
				for (File domStateHtml : allStatesFiles) {

					String stateName = domStateHtml.getName();
					// Remove .html
					String correspondingCSSFolderName = stateName.substring(0, stateName.length() - 5);

					try {

						CSSAnalyser cssAnalyser = new CSSAnalyser(domStateHtml.getAbsolutePath(), outputFolderPath + "css/" + correspondingCSSFolderName);
						cssAnalyser.analyse(params.getFPGrowthMinsup());

					} catch (FileNotFoundException fnfe) {
						LOGGER.warn(fnfe.getMessage());
					}

				}

			}

			break;
		}
		case FOLDER: {

			List<String> folders = getFolderPathsFromParameters(params);

			if (folders.size() == 0) {
				LOGGER.error("Please provide an input folder with --infolder:in/folder or list of folders using --foldersfile:path/to/file.");
			} else {

				for (String folder : folders) {
					List<File> allStatesFiles = IOHelper.searchForFiles(folder + "crawljax/doms", "html");	
					if (allStatesFiles.size() == 0) {
						LOGGER.warn("No HTML file found in " + folder + "crawljax/doms, skipping this folder");
					} else {
						for (File domStateHtml : allStatesFiles) {

							String stateName = domStateHtml.getName();
							// Remove .html
							String correspondingCSSFolderName = stateName.substring(0, stateName.length() - 5);

							try {

								CSSAnalyser cssAnalyser = new CSSAnalyser(domStateHtml.getAbsolutePath(), folder + "css/" + correspondingCSSFolderName);
								cssAnalyser.analyse(params.getFPGrowthMinsup());

							} catch (FileNotFoundException fnfe) {
								LOGGER.warn(fnfe.getMessage());
							}

						}
					}
				}
			}
			break;
		}
		case NODOM: {

			CSSAnalyser cssAnalyser = null;
			if (params.getInputFolderPath() != null) {
				try {
					cssAnalyser = new CSSAnalyser(params.getInputFolderPath());
				} catch (FileNotFoundException fnfe) {
					LOGGER.warn(fnfe.getMessage());
				}
			} else {
				LOGGER.error("Please provide an input folder with --infolder:in/folder");
				return;
			}
			cssAnalyser.analyse(params.getFPGrowthMinsup());
			break;

		}
		case DIFF: {
			throw new RuntimeException("Not yet implemented");
		}
		case PREP: {

			List<String> folders = getFolderPathsFromParameters(params);
			LessPrinter lessPrinter = new LessPrinter();
			String outFolder = params.getOutputFolderPath();

			if (folders.size() > 0) {

				for (String folder : folders) {
					List<File> allStatesFiles = IOHelper.searchForFiles(folder + "crawljax/doms", "html");	
					if (allStatesFiles.size() == 0) {
						LOGGER.warn("No HTML file found in " + folder + "crawljax/doms, skipping this folder");
					} else {
						for (File domStateHtml : allStatesFiles) {

							String stateName = domStateHtml.getName();
							// Remove .html
							String correspondingCSSFolderName = stateName.substring(0, stateName.length() - 5);

							FileLogger.addFileAppender(folder + "css/log.log", false);
							List<File> cssFiles = IOHelper.searchForFiles(folder + "css/" + correspondingCSSFolderName, "css");

							for (File f : cssFiles) {
								try {
									CSSParser parser = CSSParserFactory.getCSSParser(CSSParserType.LESS);
									StyleSheet styleSheet = parser.parseExternalCSS(f.getAbsolutePath());
									LessMigrationOpportunitiesDetector preprocessorOpportunities = new LessMigrationOpportunitiesDetector(styleSheet);
									List<LessMixinMigrationOpportunity> migrationOpportunities = preprocessorOpportunities.findMixinOpportunities();
									Collections.sort(migrationOpportunities, new Comparator<LessMixinMigrationOpportunity>() {
										@Override
										public int compare(LessMixinMigrationOpportunity o1, LessMixinMigrationOpportunity o2) {
											if (o1.getRank() == o2.getRank()) {
												return 1;
											}
											return Double.compare(o1.getRank(), o2.getRank());
										}
									});
									int i = 0;
									for (LessMixinMigrationOpportunity migrationOpportunity : migrationOpportunities) {

										boolean preservesPresentation = migrationOpportunity.preservesPresentation();
										if (!preservesPresentation) {
											LOGGER.warn("The following migration opportunity do not preserve the presentation:");
										}
										String path = outFolder + f.getName() + "migrated" + ++i + ".less";
										IOHelper.writeStringToFile(lessPrinter.getString(migrationOpportunity.apply()), path);
										LOGGER.info("Created Mixin {}, new file has been written to {}", migrationOpportunity.getMixinName(), path);
									}

								}
								catch (ParseException e) {
									LOGGER.warn("Parse exception in parsing " + f.getAbsolutePath());
								}
							}

						}
					}
				}

			} else if (null != params.getFilePath() && !"".equals(params.getFilePath())) {
				try {

					CSSParser parser = CSSParserFactory.getCSSParser(CSSParserType.LESS);
					StyleSheet styleSheet = parser.parseExternalCSS(params.getFilePath());
					LessMigrationOpportunitiesDetector preprocessorOpportunities = new LessMigrationOpportunitiesDetector(styleSheet);
					Iterable<LessMixinMigrationOpportunity> refactoringOpportunities = preprocessorOpportunities.findMixinOpportunities();
					System.out.println(refactoringOpportunities);

				} catch (ParseException e) {
					e.printStackTrace();
				}
			}
			else 
				LOGGER.error("No CSS file is provided.");
			break;
		}
		case EMPIRICAL_STUDY: {
			List<String> folders = CSSAnalyserCLI.getFolderPathsFromParameters(params);
			String outfolder = params.getOutputFolderPath();
			EmpiricalStudy.doEmpiricalStudy(folders, outfolder);
			break;
		}
		case INLINE_IMPORTS: {
			String inputFile = params.getFilePath();
			File file = new File(inputFile);
			if (file.exists()) {
				ImportInliner.replaceImports(inputFile, false);
			} else {
				LOGGER.error("File %s not found.", file.getCanonicalPath());
			}
			break;
		}
		default:
		}		
	}



	private static List<String> getFolderPathsFromParameters(ParametersParser params) {
		List<String> folders = new ArrayList<>();

		if (params.getInputFolderPath() != null)
			folders.add(params.getInputFolderPath());
		else if (params.getListOfFoldersPathsToBeAnayzedFile() != null) {
			folders.addAll(params.getFoldersListToBeAnalyzed());
		} else {
			return new ArrayList<>();
		}
		return folders;
	}




}