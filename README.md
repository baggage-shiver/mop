# Fine Evolution-Aware Runtime Verification

## Projects and data

This repository contains the following data at locations in the following hyperlinks:

* [Project list used for evaluation and their statistics](data/projects)
* [Revisions use for each project](data/revisions)
* [Raw experiment data for the time of each algorithm](data/experiments/time)
* [Raw experiment data for the safety of each algorithm](data/experiments/safety)
* [Raw experiment data for comparing and combining with RTS](data/experiments/rts)

## Repository structure

| Directory          | Purpose                                                      |
| ------------------ | ------------------------------------------------------------ |
| Docker             | Contains a Dockerfile that can be built to run experiments.  |
| data               | Contains project information and experiment data.            |
| emop               | Contains the source code of FineMOP.                         |
| starts             | Contains the source code of a modified version of STARTS that eMOP and FineMOP depends on. |
| scripts            | Contains the scripts to run experiments.                     |
| local_dependencies | Contains some Maven extensions that the experiment needs.    |

## Usage

### Prerequisites

* An x86-64 architecture machine
* Ubuntu 22.04
* [Docker](https://docs.docker.com/get-docker/)

### Setup

First, you need to build a Docker image (might take a short while). Run the following commands in terminal:

```bash
docker build -f Docker/Dockerfile . --tag=finemop
```

### Run experiment

Execute the following command to run FineMOP experiment on a sample project list containing only 1 project with at most 20 revisions using eMOP, all algorithms in FineMOP, as well as Maven test and JavaMOP for each revision.

```bash
cd scripts
bash run_sequential_with_docker.sh ../data/projects/single-project-list.txt nostats 20
```

The following command will go to the `scripts` directory and execute the experiment with the full project list. **This experiment might take days to weeks to finish:**

```bash
cd scripts
bash run_sequential_with_docker.sh ../data/projects/projects-list.txt nostats 20
```

## Results

After you finished executing the command for a list of projects, you will find their run time and safety data [here](data/generated-data). Path: `data/generated-data`

Their logs will be generated on a per-project basis to [here](logs). Path: `logs`

Note that these locations are generated only after the experiment run is finished. They do not exist before experiment run.
